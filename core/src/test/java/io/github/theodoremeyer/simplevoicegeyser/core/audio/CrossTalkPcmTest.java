package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrossTalkPcmTest {
    @Test void preservesSpeakerAndStereoSamples() {
        UUID sender = UUID.fromString("12345678-1234-5678-9abc-def012345678");
        short[] samples = { 16384, -16384, Short.MAX_VALUE, Short.MIN_VALUE };
        byte[] frame = new AudioByteCompiler().compileCrossTalkPcm(sender, samples);
        ByteBuffer reader = ByteBuffer.wrap(frame);
        assertEquals(28, frame.length);
        assertEquals(0x43545031, reader.getInt());
        assertEquals(sender.getMostSignificantBits(), reader.getLong());
        assertEquals(sender.getLeastSignificantBits(), reader.getLong());
        reader.order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : samples) assertEquals(sample, reader.getShort());
    }
}
