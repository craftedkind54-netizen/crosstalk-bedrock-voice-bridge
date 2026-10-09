package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.audiolistener.PlayerAudioListener;
import de.maxhenkel.voicechat.api.audiosender.AudioSender;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.data.PlayerVcPswd;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.*;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.HttpVoiceServlet;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.*;
import org.json.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.lang.reflect.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class HttpVoiceCallTest {
    @TempDir Path folder;
    private Object previousFloodgate;
    @org.junit.jupiter.api.AfterEach void restoreFloodgate() throws Exception {
        Field field = org.geysermc.floodgate.api.InstanceHolder.class.getDeclaredField("api");
        field.setAccessible(true); field.set(null, previousFloodgate);
    }
    @Test void authenticatedHttpsCallExchangesAudioAndCleansUpOnEndAndPlayerLeave() throws Exception {
        runCall(false);
    }
    @Test void proxyBedrockWithoutLocalFloodgateSessionReceivesCodeAndExchangesAudio() throws Exception {
        runCall(true);
    }
    private void runCall(boolean proxy) throws Exception {
        var core = new SvgCore(new SvgAudioListenerTest.FakePlatform() {
            @Override public boolean isProxyForwardingEnabled() { return proxy; }
            @Override public File getDataFolder() { return folder.toFile(); }
            @Override public boolean isDependencyEnabled(String name) { return name.equals("floodgate"); }
        });
        SvgCore.getConfig().getFile().set("client.trusted-proxy-bedrock.enabled", proxy);
        java.util.concurrent.atomic.AtomicReference<String> privateMessage = new java.util.concurrent.atomic.AtomicReference<>();
        var player = new SvgPlayer() {
            final UUID uuid = proxy ? new UUID(0, 123456789L) : UUID.randomUUID();
            public UUID getUniqueId() { return uuid; }
            public String getName() { return ".TestBedrock"; }
            public boolean hasPermission(String p) { return true; }
            public boolean isOnline() { return true; }
            public Object getPlayer() { return null; }
            public void chat(String m) {}
            public void sendMessage(String m) { privateMessage.set(m); }
        };
        var floodgatePlayer = fake(org.geysermc.floodgate.api.player.FloodgatePlayer.class,
                (p,m,a) -> m.getName().equals("getUsername") ? "TestBedrock" : null);
        var floodgate = fake(org.geysermc.floodgate.api.FloodgateApi.class, (p,m,a) -> switch(m.getName()) {
            case "getPlayer" -> !proxy && player.getUniqueId().equals(a[0]) ? floodgatePlayer : null;
            case "isFloodgatePlayer" -> !proxy && player.getUniqueId().equals(a[0]);
            default -> null;
        });
        Field floodgateField = org.geysermc.floodgate.api.InstanceHolder.class.getDeclaredField("api");
        floodgateField.setAccessible(true); previousFloodgate = floodgateField.get(null);
        floodgateField.set(null, floodgate);
        var passwords = new PlayerVcPswd(core);
        set(core, "playerVcPswd", passwords);
        passwords.setPassword(player, "local-test-only");
        SvgCore.getPlayerManager().addPlayer(player);
        privateMessage.set(null);
        AtomicInteger uploaded = new AtomicInteger(), removed = new AtomicInteger();
        VoicechatConnection connection = fake(VoicechatConnection.class, (p,m,a) -> m.getReturnType() == boolean.class ? false : null);
        AudioSender sender = fake(AudioSender.class, (p,m,a) -> { if (m.getName().equals("send")) uploaded.incrementAndGet(); return m.getReturnType() == boolean.class ? true : null; });
        OpusEncoder encoder = fake(OpusEncoder.class, (p,m,a) -> m.getName().equals("encode") ? new byte[]{1,2} : null);
        var listener = fake(PlayerAudioListener.class, (p,m,a) -> null);
        var builder = fake(PlayerAudioListener.Builder.class, (p,m,a) -> m.getName().equals("build") ? listener : p);
        var api = fake(VoicechatServerApi.class, (p,m,a) -> switch(m.getName()) {
            case "getConnectionOf" -> connection;
            case "createAudioSender" -> sender;
            case "createEncoder" -> encoder;
            case "playerAudioListenerBuilder" -> builder;
            case "registerAudioListener", "registerAudioSender" -> true;
            case "unregisterAudioSender" -> { removed.incrementAndGet(); yield null; }
            case "getAudioConverter" -> fake(m.getReturnType(), (p2,m2,a2) -> m2.getName().equals("bytesToShorts") ? new short[960] : null);
            default -> null;
        });
        var bridge = new VoiceChatBridge(); set(bridge, "serverApi", api); set(core, "vcBridge", bridge);
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server); connector.setHost("127.0.0.1"); connector.setPort(0); server.addConnector(connector);
        ServletContextHandler context = new ServletContextHandler(); context.setContextPath("/"); server.setHandler(context);
        context.addServlet(new ServletHolder(new HttpVoiceServlet()), "/api/voice/*");
        try {
            server.start();
            String base = "http://127.0.0.1:" + connector.getLocalPort() + "/api/voice/";
            if (proxy) {
                SvgCore.getConfig().getFile().set("client.trusted-proxy-bedrock.enabled", false);
                assertEquals(400, post(base + "code", new JSONObject().put("username", "TestBedrock"), null).statusCode());
                assertNull(privateMessage.get());
                SvgCore.getConfig().getFile().set("client.trusted-proxy-bedrock.enabled", true);
            }
            var sent = post(base + "code", new JSONObject().put("username", "TestBedrock"), null);
            assertEquals(200, sent.statusCode());
            assertFalse(sent.body().contains("code:"));
            String code = privateMessage.get().split("code: ")[1].substring(0,6);
            assertFalse(sent.body().contains(code));
            assertEquals(400, post(base + "code", new JSONObject().put("username", "TestBedrock"), null).statusCode());
            assertEquals(403, post(base + "join", new JSONObject().put("username", "TestBedrock").put("password", "local-test-only"), null).statusCode());
            var login = new JSONObject().put("username", "TestBedrock").put("code", code).put("build", SvgCore.BUILD_ID);
            var wrongCase = post(base + "join", new JSONObject(login.toString()).put("username", "testbedrock"), null);
            assertEquals(403, wrongCase.statusCode());
            assertFalse(new JSONObject(wrongCase.body()).has("token"));
            var denied = post(base + "join", new JSONObject(login.toString()).put("code", "wrong!"), null);
            assertEquals(403, denied.statusCode()); assertFalse(new JSONObject(denied.body()).has("token"));
            var joined = post(base + "join", login, null);
            assertEquals(200, joined.statusCode(), joined.body());
            var body = new JSONObject(joined.body()); String token = body.getString("token");
            assertEquals(43, token.length()); assertTrue(body.toString().contains("Connected as"));
            var live = SvgCore.getConnectionManager().get(player.getUniqueId());
            assertTrue(live.isAuthenticated());
            Field channel = live.getClass().getDeclaredField("session"); channel.setAccessible(true);
            ((VoiceTransport)channel.get(live)).sendBinary(new byte[]{9,8,7});
            var exchange = post(base + "exchange", new JSONObject().put("audio", new JSONArray().put(Base64.getEncoder().encodeToString(new byte[1920]))), token);
            assertEquals(200, exchange.statusCode()); assertEquals(1, uploaded.get());
            assertArrayEquals(new byte[]{9,8,7}, Base64.getDecoder().decode(new JSONObject(exchange.body()).getJSONArray("audio").getString(0)));
            post(base + "close", new JSONObject(), token);
            assertNull(SvgCore.getConnectionManager().get(player.getUniqueId())); assertEquals(1, removed.get());
            assertEquals(401, post(base + "exchange", new JSONObject().put("audio", new JSONArray()), token).statusCode());
            assertEquals(403, post(base + "join", login, null).statusCode()); // single-use
            io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket.AUTHENTICATOR.loginCodes.invalidate(player.getUniqueId());
            assertEquals(200, post(base + "code", new JSONObject().put("username", "TestBedrock"), null).statusCode());
            login.put("code", privateMessage.get().split("code: ")[1].substring(0,6));
            token = new JSONObject(post(base + "join", login, null).body()).getString("token");
            SvgCore.getPlayerManager().removePlayer(player);
            var left = post(base + "exchange", new JSONObject().put("audio", new JSONArray()), token);
            assertTrue(left.statusCode() == 401 || !new JSONObject(left.body()).getBoolean("open"));
            assertEquals(2, removed.get());
        } finally { server.stop(); SvgCore.disable(); }
    }
    private static HttpResponse<String> post(String url, JSONObject body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return HttpClient.newHttpClient().send(request.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field); f.setAccessible(true); f.set(target,value);
    }
    private static <T> T fake(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
