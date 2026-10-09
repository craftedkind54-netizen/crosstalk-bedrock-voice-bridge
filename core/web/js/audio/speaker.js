// A bounded jitter buffer for each speaker, mixed on the browser audio thread.
class SpeakerProcessor extends AudioWorkletProcessor {
    constructor() {
        super();
        this.streams = new Map();
        this.capacity = 48000;
        this.target = 1920;
        this.port.onmessage = ({ data }) => {
            if (data.type === "buffer-ms") {
                this.target = Math.round(Math.max(40, Math.min(500, Number(data.milliseconds) || 40)) * 48);
                return;
            }
            if (data.type === "reset") { this.streams.clear(); return; }
            if (data.type !== "pcm") return;
            const packet = data.buffer instanceof Float32Array
                ? { samples: data.buffer, channels: 1 } : data.buffer;
            if (!(packet?.samples instanceof Float32Array)) return;
            const id = packet.streamId || "default";
            let stream = this.streams.get(id);
            if (!stream) {
                if (this.streams.size >= 64) this.streams.delete(this.streams.keys().next().value);
                stream = { left: new Float32Array(this.capacity), right: new Float32Array(this.capacity), read: 0, write: 0, available: 0, waiting: 0, idle: 0, started: false };
                this.streams.set(id, stream);
            }
            stream.idle = 0;
            const channels = packet.channels === 2 ? 2 : 1;
            for (let i = 0; i + channels <= packet.samples.length; i += channels) {
                if (stream.available === this.capacity) {
                    stream.read = (stream.read + 1) % this.capacity;
                    stream.available--;
                }
                stream.left[stream.write] = packet.samples[i];
                stream.right[stream.write] = packet.samples[i + channels - 1];
                stream.write = (stream.write + 1) % this.capacity;
                stream.available++;
            }
        };
    }

    process(inputs, outputs) {
        const left = outputs[0]?.[0];
        if (!left) return true;
        const right = outputs[0][1];
        left.fill(0);
        right?.fill(0);
        for (const [id, stream] of this.streams) {
            if (!stream.available) {
                stream.idle += left.length;
                stream.started = false; stream.waiting = 0;
                if (stream.idle > 240000) this.streams.delete(id);
                continue;
            }
            if (!stream.started) {
                stream.waiting += left.length;
                // HTTPS arrives in batches: retain a playout cushion between responses.
                // A timeout also plays a short word that never fills the cushion.
                // Large HTTPS batches must still wait: starting immediately when a batch
                // fills the target leaves no reserve for a later, slower response.
                if (stream.waiting < this.target && (this.target > 1920 || stream.available < this.target)) continue;
                stream.started = true;
            }
            for (let i = 0; i < left.length && stream.available; i++) {
                left[i] += stream.left[stream.read];
                if (right) right[i] += stream.right[stream.read];
                stream.read = (stream.read + 1) % this.capacity;
                stream.available--;
            }
        }
        for (let i = 0; i < left.length; i++) {
            left[i] = Math.max(-1, Math.min(1, left[i]));
            if (right) right[i] = Math.max(-1, Math.min(1, right[i]));
        }
        return true;
    }
}
registerProcessor("pcm-player", SpeakerProcessor);
