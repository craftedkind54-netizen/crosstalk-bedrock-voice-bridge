package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class HttpVoiceTransportTest {
    @Test void preservesAudioAndMessagesAndDrainsOnlyOnce() {
        var transport = new HttpVoiceTransport("test");
        byte[] frame = {1, 2, 3, 4};
        transport.sendBinary(frame);
        frame[0] = 99;
        transport.sendText("hello");
        var result = transport.drain();
        assertArrayEquals(new byte[]{1, 2, 3, 4}, Base64.getDecoder().decode(result.getJSONArray("audio").getString(0)));
        assertEquals("hello", result.getJSONArray("messages").getString(0));
        assertTrue(result.getBoolean("open"));
        assertEquals(0, transport.drain().getJSONArray("audio").length());
    }
    @Test void slowConsumersHaveBoundedQueues() {
        var transport = new HttpVoiceTransport("test");
        for (int i = 0; i < 600; i++) { transport.sendBinary(new byte[]{(byte)i}); transport.sendText("m" + i); }
        var result = transport.drain();
        assertEquals(512, result.getJSONArray("audio").length());
        assertEquals(32, result.getJSONArray("messages").length());
        assertEquals("m568", result.getJSONArray("messages").getString(0));
    }
    @Test void closeDropsAudioButDeliversFinalError() {
        var transport = new HttpVoiceTransport("test");
        transport.sendBinary(new byte[]{1}); transport.sendText("Player left");
        transport.close(4003, "Player left");
        transport.sendBinary(new byte[]{2}); transport.sendText("late");
        var result = transport.drain();
        assertFalse(result.getBoolean("open"));
        assertEquals(4003, result.getInt("code"));
        assertEquals(0, result.getJSONArray("audio").length());
        assertEquals(1, result.getJSONArray("messages").length());
    }
    @Test void staleAudioIsDiscarded() throws Exception {
        var transport = new HttpVoiceTransport("test");
        transport.sendBinary(new byte[]{1});
        Thread.sleep(1100);
        assertEquals(0, transport.drain().getJSONArray("audio").length());
    }
}
