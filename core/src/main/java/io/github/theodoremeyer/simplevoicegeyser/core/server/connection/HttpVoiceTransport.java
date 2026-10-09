package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import java.util.ArrayDeque;
import java.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded, short-lived audio queue. Slow listeners drop old audio instead of accumulating delay. */
public final class HttpVoiceTransport implements VoiceTransport {
    private record Frame(long time, byte[] bytes) {}
    private final ArrayDeque<Frame> audio = new ArrayDeque<>();
    private final ArrayDeque<String> messages = new ArrayDeque<>();
    private final String remote;
    private boolean open = true;
    private int code;
    private String reason = "";

    public HttpVoiceTransport(String remote) { this.remote = remote; }
    public synchronized boolean isOpen() { return open; }
    public String remoteAddress() { return "HTTPS " + remote; }
    public synchronized void sendText(String text) {
        if (!open) return;
        if (messages.size() >= 32) messages.removeFirst();
        messages.addLast(text);
    }
    public synchronized void sendBinary(byte[] bytes) {
        if (!open || bytes.length > 16384) return;
        while (audio.size() >= 512) audio.removeFirst();
        audio.addLast(new Frame(System.nanoTime(), bytes.clone()));
    }
    public synchronized void close(int code, String reason) {
        if (!open) return;
        open = false;
        this.code = code;
        this.reason = reason;
        audio.clear();
    }
    public synchronized JSONObject drain() {
        JSONArray texts = new JSONArray();
        while (!messages.isEmpty()) texts.put(messages.removeFirst());
        JSONArray frames = new JSONArray();
        long now = System.nanoTime();
        while (!audio.isEmpty()) {
            Frame frame = audio.removeFirst();
            if (now - frame.time() <= 1_000_000_000L) frames.put(Base64.getEncoder().encodeToString(frame.bytes()));
        }
        return new JSONObject().put("open", open).put("code", code).put("reason", reason)
                .put("messages", texts).put("audio", frames);
    }
}
