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
        final UUID id; final String name;
        Set<UUID> nearby=new HashSet<>(), hidden=new HashSet<>();
        @Override public boolean canSee(SvgPlayer p){return !hidden.contains(p.getUniqueId());}
        @Override public boolean isNearby(SvgPlayer p,double r){return r==48D && nearby.contains(p.getUniqueId());} boolean online=true,allowed=true; String message;
        Player(String n){this(n,UUID.randomUUID());}
        Player(String n,UUID uuid){name=n;id=uuid;}
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
            case "getVoiceChatDistance"->48D;
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
    @Test void bedrockCreatesGroupInvitesBothEditionsAndRosterTracksProximity() throws Exception {
        var core=new SvgCore(new SvgAudioListenerTest.FakePlatform(){@Override public boolean isProxyForwardingEnabled(){return true;}});
        SvgCore.getConfig().getFile().set("client.trusted-proxy-bedrock.enabled",true);
        var owner=new Player(".Owner",new UUID(0,12345));var bedrock=new Player(".Friend",new UUID(0,56789));var java=new Player("JavaFriend");
        for(var p:List.of(owner,bedrock,java))SvgCore.getPlayerManager().addPlayer(p);
        Map<UUID,Group> groups=new HashMap<>(), membership=new HashMap<>();
        String[] groupName={""};UUID groupId=UUID.randomUUID();
        var built=fake(Group.class,(p,m,a)->switch(m.getName()){
            case "getId"->groupId;case "getName"->groupName[0];default->m.getReturnType()==boolean.class?false:null;
        });
        var api=fake(VoicechatServerApi.class,(p,m,a)->switch(m.getName()){
            case "getVoiceChatDistance"->48D;case "getGroups"->groups.values();case "getGroup"->groups.get(a[0]);
            case "groupBuilder"->fake(Group.Builder.class,(p2,m2,a2)->{
                if(m2.getName().equals("setName"))groupName[0]=(String)a2[0];
                if(m2.getName().equals("build")){groups.put(groupId,built);return built;}return p2;
            });
            case "getConnectionOf"->fake(VoicechatConnection.class,(p2,m2,a2)->switch(m2.getName()){
                case "getGroup"->membership.get(a[0]);case "setGroup"->{membership.put((UUID)a[0],(Group)a2[0]);yield null;}
                case "isConnected"->true;default->m2.getReturnType()==boolean.class?false:null;
            });default->null;
        });
        var bridge=new VoiceChatBridge();set(bridge,"serverApi",api);set(core,"vcBridge",bridge);var service=new BrowserGroups();
        var created=service.handle(owner,new JSONObject().put("action","create").put("name","Adventure"));
        assertEquals("Adventure",created.getJSONObject("current").getString("name"));
        assertEquals("Bedrock",created.getJSONObject("self").getString("edition"));
        service.invite(owner,bedrock);service.invite(owner,java);
        assertTrue(service.hasInvite(bedrock,groupId.toString()));assertTrue(service.hasInvite(java,groupId.toString()));
        service.accept(bedrock,groupId.toString());service.accept(java,groupId.toString());
        assertEquals(3,service.state(owner).getJSONArray("members").length());
        service.handle(java,new JSONObject().put("action","leave"));owner.nearby.add(java.id);
        var state=service.state(owner);assertEquals(2,state.getJSONArray("members").length());
        assertEquals("Java",state.getJSONArray("nearby").getJSONObject(0).getString("edition"));
        owner.hidden.add(java.id);assertEquals(0,service.state(owner).getJSONArray("nearby").length());
        assertThrows(IllegalArgumentException.class,()->service.invite(owner,java));
        owner.hidden.clear();owner.nearby.clear();assertEquals(0,service.state(owner).getJSONArray("nearby").length());
        java.online=false;assertEquals(1,service.state(owner).getJSONArray("players").length());
    }
    static void set(Object o,String name,Object v)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,v);}
    static <T>T fake(Class<T> t,InvocationHandler h){return t.cast(Proxy.newProxyInstance(t.getClassLoader(),new Class<?>[]{t},h));}
}
