package io.github.theodoremeyer.simplevoicegeyser.core.audio;
import java.util.ArrayDeque;

/** Bounded pacing queue for microphone packets received in HTTPS batches.
 * Access is serialized by the owning call.
 */
public final class PcmFrameQueue {
    private record Frame(byte[] pcm, long time) {}
    private final ArrayDeque<Frame> frames = new ArrayDeque<>();
    private long next;
    public void add(byte[] pcm, long now) {
        if (frames.isEmpty()) next = now + 160_000_000L;
        while (frames.size() >= 40) frames.removeFirst();
        frames.addLast(new Frame(pcm.clone(), now));
    }
    public byte[] poll(long now) {
        while (!frames.isEmpty() && now - frames.peekFirst().time() > 1_000_000_000L) frames.removeFirst();
        if (frames.isEmpty() || now < next) return null;
        next = now - next >= 20_000_000L ? now + 20_000_000L : next + 20_000_000L;
        return frames.removeFirst().pcm();
    }
    public void clear() { frames.clear(); next = 0; }
}
