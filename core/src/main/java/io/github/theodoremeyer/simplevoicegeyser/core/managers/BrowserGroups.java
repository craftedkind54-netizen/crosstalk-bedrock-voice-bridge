package io.github.theodoremeyer.simplevoicegeyser.core.managers;

import de.maxhenkel.voicechat.api.*;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import java.util.*;
import org.json.*;

/** Group controls shared by the website and Bedrock in-game invitations. */
public final class BrowserGroups {
    private record Invite(UUID group, SvgPlayer from, SvgPlayer to, long expires) {}
    private final Map<String, Invite> invites = new HashMap<>();
    private final java.util.function.LongSupplier clock;
    public BrowserGroups() { this(System::currentTimeMillis); }
    public BrowserGroups(java.util.function.LongSupplier clock) { this.clock = clock; }
    private VoicechatServerApi api() {
        var api = SvgCore.getBridge().getVcServerApi();
        if (api == null) throw new IllegalArgumentException("Voice chat is starting. Try again.");
        return api;
    }
    private void check(SvgPlayer player) {
        if (player == null || !player.isOnline()) throw new IllegalArgumentException("Join Minecraft first.");
        if (!player.hasPermission("svg.vc.group.join")) throw new IllegalArgumentException("You cannot use voice groups on this server.");
        var config = api().getServerConfig();
        if (config != null && !config.getBoolean("enable_groups", true)) throw new IllegalArgumentException("Groups are disabled on this server.");
    }
    private VoicechatConnection connection(SvgPlayer p) {
        var c = api().getConnectionOf(p.getUniqueId());
        if (c == null) throw new IllegalArgumentException("Start voice chat first.");
        return c;
    }
    private Group group(String id) {
        Group g;
        try { g = api().getGroup(UUID.fromString(id)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Choose a group first."); }
        if (g == null) throw new IllegalArgumentException("That group no longer exists.");
        return g;
    }
    private void prune() {
        long now = clock.getAsLong();
        invites.values().removeIf(i -> i.expires < now || !i.from.isOnline() || !i.to.isOnline()
                || api().getGroup(i.group) == null);
    }
    public synchronized String invite(SvgPlayer from, SvgPlayer to) {
        check(from); check(to); prune();
        var g = connection(from).getGroup();
        if (g == null) throw new IllegalArgumentException("Join or create a group before inviting someone.");
        if (from.getUniqueId().equals(to.getUniqueId())) throw new IllegalArgumentException("Choose another player.");
        String key = to.getUniqueId() + ":" + g.getId();
        var old = invites.get(key);
        long now = clock.getAsLong();
        if (old != null && old.expires > now + 270000) throw new IllegalArgumentException("An invite was already sent. Wait a moment.");
        if (invites.size() >= 1024) throw new IllegalArgumentException("Too many pending invites. Try again later.");
        invites.put(key, new Invite(g.getId(), from, to, now + 300000));
        to.sendMessage("[CrossTalk] " + from.getName() + " invited you to voice group " + g.getName()
                + ". Accept on the voice website or type /voicechat join " + g.getId() + " (expires in 5 minutes).");
        return "Invite sent to " + to.getName() + ".";
    }
    public synchronized boolean hasInvite(SvgPlayer player, String id) {
        prune();
        var i = invites.get(player.getUniqueId() + ":" + id);
        return i != null && i.to == player;
    }
    public synchronized void accept(SvgPlayer player, String id) {
        check(player);
        if (!hasInvite(player,id)) throw new IllegalArgumentException("That invite expired. Ask for a new invite.");
        connection(player).setGroup(group(id));
        invites.remove(player.getUniqueId() + ":" + id);
    }
    public synchronized JSONObject handle(SvgPlayer player, JSONObject request) {
        check(player);
        String message = "";
        switch (request.optString("action", "list")) {
            case "list" -> {}
            case "leave" -> { connection(player).setGroup(null); message = "Back in proximity chat."; }
            case "accept" -> { accept(player,request.optString("id")); message = "Invite accepted."; }
            case "decline" -> invites.remove(player.getUniqueId() + ":" + request.optString("id"));
            case "invite" -> {
                SvgPlayer target = null;
                for (var p : SvgCore.getPlayerManager().getAllPlayers()) {
                    if (p.getUniqueId().toString().equals(request.optString("player"))) target = p;
                }
                if (target == null) throw new IllegalArgumentException("That player left the server.");
                message = invite(player,target);
            }
            case "join" -> {
                var g = group(request.optString("id"));
                if (g.isHidden()) throw new IllegalArgumentException("Ask a group member to invite you.");
                if (!passwordMatches(g, request.optString("password"))) throw new IllegalArgumentException("Incorrect group password. You can also ask for an invite.");
                connection(player).setGroup(g); message = "Joined " + g.getName() + ".";
            }
            case "create" -> {
                if (!player.hasPermission("svg.vc.group.create")) throw new IllegalArgumentException("You cannot create groups.");
                String name = request.optString("name").trim(), password = request.optString("password");
                if (name.isEmpty() || name.length() > 32 || name.chars().anyMatch(c -> c < 32 || c == 167) || password.length() > 64)
                    throw new IllegalArgumentException("Use a group name of 1–32 characters and a password up to 64 characters.");
                for (var g : api().getGroups()) if (g.getName().equals(name)) throw new IllegalArgumentException("That group name is already in use.");
                var c = connection(player);
                var builder = api().groupBuilder().setName(name).setType(Group.Type.NORMAL).setPersistent(false);
                if (!password.isEmpty()) builder.setPassword(password);
                c.setGroup(builder.build()); message = "Group created.";
            }
            default -> throw new IllegalArgumentException("Unknown group action.");
        }
        return state(player).put("message", message);
    }
    public synchronized JSONObject state(SvgPlayer player) {
        check(player); prune();
        var list = new JSONArray();
        for (var g : api().getGroups()) if (!g.isHidden()) list.put(describe(g));
        var pending = new JSONArray();
        for (var i : invites.values()) if (i.to == player) pending.put(describe(group(i.group.toString())).put("from", i.from.getName()));
        var players = new JSONArray();
        for (var p : SvgCore.getPlayerManager().getAllPlayers()) if (p.isOnline() && !p.getUniqueId().equals(player.getUniqueId()))
            players.put(new JSONObject().put("id", p.getUniqueId()).put("name", p.getName()));
        var current = connection(player).getGroup();
        return new JSONObject().put("type", "groups").put("groups", list).put("invites", pending).put("players", players)
                .put("current", current == null ? JSONObject.NULL : describe(current));
    }
    private JSONObject describe(Group g) { return new JSONObject().put("id",g.getId()).put("name",g.getName()).put("locked",g.hasPassword()); }
    // SVC's API exposes hasPassword but no password validator. Fail closed if internals change.
    public static boolean passwordMatches(Group group, String supplied) {
        if (!group.hasPassword()) return true;
        try {
            var field = group.getClass().getDeclaredField("group"); field.setAccessible(true);
            var internal = field.get(group);
            var password = internal.getClass().getDeclaredField("password"); password.setAccessible(true);
            var actual = password.get(internal);
            return actual instanceof String && actual.equals(supplied);
        } catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }
}
