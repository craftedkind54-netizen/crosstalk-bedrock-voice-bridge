package io.github.theodoremeyer.simplevoicegeyser.core.server.servlets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.HttpVoiceTransport;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Same-origin HTTPS fallback; credentials only on join, random bearer tokens thereafter. */
public final class HttpVoiceServlet extends HttpServlet {
    private static final int MAX_BODY = 131072;
    private final Map<String, Call> calls = new ConcurrentHashMap<>();
    private final Semaphore joins = new Semaphore(4);
    private final SecureRandom random = new SecureRandom();
    private ScheduledExecutorService cleanup;

    private static final class Call {
        final HttpVoiceTransport transport;
        final JettyWebSocket protocol;
        final io.github.theodoremeyer.simplevoicegeyser.core.audio.PcmFrameQueue incoming = new io.github.theodoremeyer.simplevoicegeyser.core.audio.PcmFrameQueue();
        long lastSeen = System.nanoTime();
        boolean disposed;
        Call(String remote) {
            transport = new HttpVoiceTransport(remote);
            protocol = new JettyWebSocket();
            protocol.openTransport(transport);
            protocol.setAudioNegotiation(new io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioSessionNegotiation(
                    io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioTransportMode.LEGACY, true));
        }
        synchronized void dispose() {
            if (disposed) return;
            disposed = true;
            incoming.clear();
            transport.close(1000, "HTTPS call ended");
            protocol.onClose(1000, "HTTPS call ended");
        }
    }

    @Override public void init() {
        cleanup = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "CrossTalk-HTTPS-cleanup"); thread.setDaemon(true); return thread;
        });
        // Restore the 20ms microphone cadence after an HTTPS batch arrives.
        // The queue enforces cadence and suppresses catch-up bursts after a stall.
        cleanup.scheduleAtFixedRate(() -> {
            for (Call call : calls.values()) {
                synchronized (call) {
                    if (call.disposed || !call.transport.isOpen()) continue;
                    byte[] pcm = call.incoming.poll(System.nanoTime());
                    if (pcm != null) {
                        try { call.protocol.onMessage(pcm, 0, pcm.length); }
                        catch (Exception error) { SvgCore.getLogger().debug("HTTPS audio failed", error); }
                    }
                }
            }
        }, 5, 5, TimeUnit.MILLISECONDS);
        cleanup.scheduleWithFixedDelay(() -> {
            for (var entry : calls.entrySet()) {
                Call call = entry.getValue();
                synchronized (call) {
                    if (!call.transport.isOpen() || System.nanoTime() - call.lastSeen > 15_000_000_000L) {
                        if (calls.remove(entry.getKey(), call)) {
                            try { call.dispose(); } catch (Exception error) { SvgCore.getLogger().debug("HTTPS cleanup failed", error); }
                        }
                    }
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    @Override public void destroy() {
        if (cleanup != null) cleanup.shutdownNow();
        calls.values().forEach(Call::dispose);
        calls.clear();
    }

    private static void reply(HttpServletResponse response, int status, JSONObject body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getWriter().write(body.toString());
    }
    private static JSONObject error(String message) { return new JSONObject().put("error", message); }

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        reply(resp, 200, new JSONObject().put("transport", "https-poll-v1").put("version", SvgCore.VERSION));
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // No CORS or cookies: foreign pages cannot submit JSON or read bearer tokens.
        if ("cross-site".equals(req.getHeader("Sec-Fetch-Site")) || req.getContentType() == null
                || !req.getContentType().split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
            reply(resp, 415, error("Use same-origin JSON requests.")); return;
        }
        byte[] bytes = req.getInputStream().readNBytes(MAX_BODY + 1);
        if (bytes.length > MAX_BODY) { reply(resp, 413, error("Request too large.")); return; }
        try {
            JSONObject body = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            if ("/code".equals(req.getPathInfo())) {
                String name = body.optString("username", "").trim();
                if (name.isEmpty() || name.length()>64) { reply(resp,400,error("Enter your Bedrock username.")); return; }
                if (!joins.tryAcquire()) { reply(resp,429,error("Try again shortly.")); return; }
                try {
                    var result = JettyWebSocket.AUTHENTICATOR.requestCode(name);
                    reply(resp,result.success()?200:400,result.success()
                            ? new JSONObject().put("message","Code sent privately in Minecraft. It expires in 2 minutes.") : error(result.message()));
                } finally { joins.release(); }
                return;
            }
            if ("/join".equals(req.getPathInfo())) { join(req, resp, body); return; }
            if (!Set.of("/exchange", "/close").contains(String.valueOf(req.getPathInfo()))) {
                reply(resp, 404, error("Unknown voice endpoint.")); return;
            }
            String header = req.getHeader("Authorization");
            String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : "";
            Call call = calls.get(token);
            if (call == null) { reply(resp, 401, error("Your voice session ended. Start a new call.")); return; }
            synchronized (call) {
                if (call.disposed || System.nanoTime() - call.lastSeen > 15_000_000_000L) {
                    calls.remove(token, call); call.dispose();
                    reply(resp, 401, error("Your voice session expired. Start a new call.")); return;
                }
                if ("/close".equals(req.getPathInfo())) {
                    calls.remove(token, call); call.dispose(); reply(resp, 200, call.transport.drain()); return;
                }
                JSONArray frames = body.optJSONArray("audio");
                if (frames == null || frames.length() > 40) { reply(resp, 400, error("Invalid audio batch.")); return; }
                // Validate the entire batch before forwarding any audio.
                List<byte[]> decoded = new ArrayList<>();
                for (int i = 0; i < frames.length(); i++) {
                    String frame = frames.getString(i);
                    if (frame.length() != 2560) throw new IllegalArgumentException("Invalid PCM frame");
                    byte[] pcm = Base64.getDecoder().decode(frame);
                    if (pcm.length != 1920) throw new IllegalArgumentException("Invalid PCM frame");
                    decoded.add(pcm);
                }
                JSONArray controls = body.optJSONArray("controls");
                if (controls != null) {
                    if (controls.length() > 4) throw new IllegalArgumentException("Too many controls");
                    for (int i=0; i<controls.length(); i++) {
                        var control = controls.getJSONObject(i);
                        if (!"groups".equals(control.optString("type")) || control.toString().length() > 2048)
                            throw new IllegalArgumentException("Invalid control");
                    }
                }
                call.lastSeen = System.nanoTime();
                if (call.transport.isOpen()) {
                    if (controls != null) for (int i=0; i<controls.length(); i++) call.protocol.onMessage(controls.getJSONObject(i).toString());
                    for (byte[] pcm : decoded) call.incoming.add(pcm, System.nanoTime());
                }
                reply(resp, 200, call.transport.drain());
            }
        } catch (JSONException | IllegalArgumentException error) {
            reply(resp, 400, error("Invalid voice request."));
        }
    }

    private void join(HttpServletRequest req, HttpServletResponse resp, JSONObject body) throws IOException {
        if (body.optString("username").length() > 64 || body.optString("code").length() > 6) {
            reply(resp, 400, error("Invalid login details.")); return;
        }
        if (!joins.tryAcquire()) { reply(resp, 503, error("Voice server busy. Try again shortly.")); return; }
        Call call = null;
        boolean retained = false;
        try {
            if (calls.size() >= 256) { reply(resp, 503, error("Voice server full.")); return; }
            call = new Call(req.getRemoteAddr());
            body.put("type", "join");
            call.protocol.onMessage(body.toString());
            var connection = call.protocol.getConnection();
            if (connection == null || !connection.isAuthenticated() || !call.transport.isOpen()) {
                reply(resp, 403, call.transport.drain()); return;
            }
            byte[] secret = new byte[32]; random.nextBytes(secret);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
            call.lastSeen = System.nanoTime();
            calls.put(token, call);
            retained = true;
            reply(resp, 200, call.transport.drain().put("token", token));
        } finally {
            if (!retained && call != null) call.dispose();
            joins.release();
        }
    }
}
