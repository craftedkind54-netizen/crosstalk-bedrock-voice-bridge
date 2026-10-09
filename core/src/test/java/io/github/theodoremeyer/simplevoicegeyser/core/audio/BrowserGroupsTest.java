package io.github.theodoremeyer.simplevoicegeyser.core.audio;
import de.maxhenkel.voicechat.api.*;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.BrowserGroups;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BrowserGroupsTest {
    static class Player extends SvgPlayer {
        final UUID id=UUID.randomUUID(); final String name; boolean online=true,allowed=true; String message;
        Player(String n){name=n;}
        public UUID getUniqueId(){return id;} public String getName(){return name;}
        public boolean hasPermission(String p){return allowed;} public boolean isOnline(){return online;}
        public Object getPlayer(){return null;} public void chat(String s){} public void sendMessage(String s){message=s;}
    }
    @Test void groupsInvitesPermissionsExpiryAndPasswordFailures() throws Exception {
        var core=new SvgCore(new SvgAudioListenerTest.FakePlatform());
        var alice=new Player("Alice"); var bob=new Player("Bob"); var stranger=new Player("Other");
        SvgCore.getPlayerManager().addPlayer(alice); SvgCore.getPlayerManager().addPlayer(bob); SvgCore.getPlayerManager().addPlayer(stranger);
        UUID openId=UUID.randomUUID(), lockedId=UUID.randomUUID(), hiddenId=UUID.randomUUID();
        Map<UUID,Group> groups=new HashMap<>();
        for(UUID id:List.of(openId,lockedId,hiddenId))groups.put(id,fake(Group.class,(p,m,a)->switch(m.getName()){
            case "getId"->id; case "getName"->id.equals(openId)?"Open":"Private";
            case "hasPassword"->!id.equals(openId);case "isHidden"->id.equals(hiddenId);
            default->m.getReturnType()==boolean.class?false:null;
        }));
        Map<UUID,Group> membership=new HashMap<>();membership.put(alice.id,groups.get(lockedId));
        var api=fake(VoicechatServerApi.class,(p,m,a)->switch(m.getName()){
            case "getGroups"->groups.values();case "getGroup"->groups.get(a[0]);
            case "getConnectionOf"->fake(VoicechatConnection.class,(p2,m2,a2)->switch(m2.getName()){
                case "getGroup"->membership.get(a[0]);case "setGroup"->{membership.put((UUID)a[0],(Group)a2[0]);yield null;}
                default->m2.getReturnType()==boolean.class?false:null;
            });default->null;
        });
        var bridge=new VoiceChatBridge();set(bridge,"serverApi",api);set(core,"vcBridge",bridge);
        long[] clock={1000};var service=new BrowserGroups(()->clock[0]);
        var state=service.state(bob);assertEquals(2,state.getJSONArray("groups").length());
        assertFalse(state.toString().contains("password"));
        assertThrows(IllegalArgumentException.class,()->service.handle(bob,new JSONObject().put("action","join").put("id",lockedId).put("password","wrong")));
        assertNull(membership.get(bob.id));
        service.handle(bob,new JSONObject().put("action","join").put("id",openId));assertSame(groups.get(openId),membership.get(bob.id));
        service.invite(alice,bob);assertTrue(bob.message.contains("invited"));
        assertEquals(1,service.state(bob).getJSONArray("invites").length());assertEquals(0,service.state(stranger).getJSONArray("invites").length());
        assertThrows(IllegalArgumentException.class,()->service.accept(stranger,lockedId.toString()));
        service.accept(bob,lockedId.toString());assertSame(groups.get(lockedId),membership.get(bob.id));
        assertThrows(IllegalArgumentException.class,()->service.accept(bob,lockedId.toString()));
        service.invite(alice,bob);clock[0]+=300001;assertFalse(service.hasInvite(bob,lockedId.toString()));
        service.invite(alice,bob);bob.online=false;assertFalse(service.hasInvite(bob,lockedId.toString()));bob.online=true;
        bob.allowed=false;assertThrows(IllegalArgumentException.class,()->service.handle(bob,new JSONObject().put("action","join").put("id",openId)));
        bob.allowed=true;service.handle(bob,new JSONObject().put("action","leave"));assertNull(membership.get(bob.id));
        assertFalse(BrowserGroups.passwordMatches(groups.get(lockedId),"anything"));
    }
    static void set(Object o,String name,Object v)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,v);}
    static <T>T fake(Class<T> t,InvocationHandler h){return t.cast(Proxy.newProxyInstance(t.getClassLoader(),new Class<?>[]{t},h));}
}
