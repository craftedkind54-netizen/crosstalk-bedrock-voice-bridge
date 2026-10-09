package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.json.JSONObject;
public final class GroupsPacket implements Packet {
    public String getType() { return "groups"; }
    public void handle(JettyWebSocket socket, JSONObject json) {
        var connection = socket.getConnection();
        if (connection == null || !connection.isAuthenticated()) return;
        if (!socket.allowGroupRequest()) {
            socket.sendJson(new JSONObject().put("type","groups").put("error","Please wait a moment, then try again.")); return;
        }
        SvgCore.getPlatform().runTask(() -> {
            if (!connection.isAuthenticated() || !socket.getSession().isOpen()) return;
            try { socket.sendJson(SvgCore.getGroupManager().browser.handle(connection.getPlayer(), json)); }
            catch (IllegalArgumentException e) { socket.sendJson(new JSONObject().put("type","groups").put("error", e.getMessage())); }
            catch (Exception e) {
                SvgCore.getLogger().debug("Group request failed", e);
                socket.sendJson(new JSONObject().put("type","groups").put("error","Could not update groups. Try again."));
            }
        });
    }
}
