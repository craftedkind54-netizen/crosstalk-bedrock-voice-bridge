package io.github.theodoremeyer.simplevoicegeyser.core.audio;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PcmFrameQueueTest {
    @Test void pacesBatchesWithoutDroppingWordsOrCatchupBursts() {
        var q = new PcmFrameQueue();
        for(int i=0;i<15;i++)q.add(new byte[]{(byte)i},0);
        assertNull(q.poll(159_000_000L));
        for(int i=0;i<15;i++) {
            long time=160_000_000L+i*20_000_000L;
            assertArrayEquals(new byte[]{(byte)i},q.poll(time));
            assertNull(q.poll(time));
        }
        assertNull(q.poll(400_000_000L));
    }
    @Test void queueIsBoundedExpiresStaleAudioAndClearsOnClose() {
        var q = new PcmFrameQueue();
        for(int i=0;i<60;i++)q.add(new byte[]{(byte)i},0);
        assertArrayEquals(new byte[]{20},q.poll(160_000_000L));
        assertNull(q.poll(1_100_000_000L));
        q.add(new byte[]{1},2_000_000_000L);
        q.clear();
        assertNull(q.poll(2_100_000_000L));
    }
}
