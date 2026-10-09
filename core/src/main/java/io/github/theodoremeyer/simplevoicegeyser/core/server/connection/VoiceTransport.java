package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.eclipse.jetty.websocket.api.Session;

/** Delivery channel shared by WebSocket and authenticated HTTPS calls. */
public interface VoiceTransport {
    boolean isOpen();
    void sendText(String text) throws IOException;
    void sendBinary(byte[] bytes) throws IOException;
    void close(int code, String reason);
    String remoteAddress();

    static VoiceTransport websocket(Session session) {
        return new VoiceTransport() {
            public boolean isOpen() { return session.isOpen(); }
            public void sendText(String text) throws IOException { session.getRemote().sendString(text); }
            public void sendBinary(byte[] bytes) throws IOException { session.getRemote().sendBytes(ByteBuffer.wrap(bytes)); }
            public void close(int code, String reason) { session.close(code, reason); }
            public String remoteAddress() { return String.valueOf(session.getRemoteAddress()); }
        };
    }
}
