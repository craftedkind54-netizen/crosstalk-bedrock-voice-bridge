import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import { SvgAudio } from "../core/web/js/audio/audio.js";

function processor() {
    let Processor;
    const context = vm.createContext({ Float32Array, Map, Math,
        AudioWorkletProcessor: class { constructor() { this.port = {}; } },
        registerProcessor: (_, implementation) => { Processor = implementation; }
    });
    vm.runInContext(readFileSync(new URL("../core/web/js/audio/speaker.js", import.meta.url), "utf8"), context);
    return new Processor();
}
function enqueue(p, id, value, frames = 1920) {
    p.port.onmessage({ data: { type: "pcm", buffer: { streamId: id, channels: 2, samples: new Float32Array(frames * 2).fill(value) } } });
}
function render(p) {
    const outputs = [[new Float32Array(128), new Float32Array(128)]];
    p.process([], outputs);
    return outputs[0];
}

test("simultaneous speakers mix instead of queuing one after another", () => {
    const p = processor();
    enqueue(p, "alice", 0.2);
    enqueue(p, "bob", 0.3);
    const [left, right] = render(p);
    assert.ok(Math.abs(left[0] - 0.5) < 0.0001);
    assert.equal(left[0], right[0]);
    assert.equal(p.streams.get("alice").available, 1792);
    assert.equal(p.streams.get("bob").available, 1792);
});
test("short utterances play even without seven buffered packets", () => {
    const p = processor();
    enqueue(p, "alice", 0.25, 960);
    let heard = false;
    for (let i = 0; i < 20; i++) heard ||= render(p)[0].some(v => v !== 0);
    assert.equal(heard, true);
});
test("latency and speaker memory are bounded; end call clears playback", () => {
    const p = processor();
    enqueue(p, "alice", 0.4, 48000);
    assert.equal(p.streams.get("alice").available, 48000);
    for (let i = 0; i < 90; i++) enqueue(p, String(i), 0.1);
    assert.equal(p.streams.size, 64);
    p.port.onmessage({ data: { type: "reset" } });
    assert.equal(p.streams.size, 0);
    assert.ok(render(p)[0].every(v => v === 0));
});
test("mute blocks microphone packets", () => {
    const audio = new SvgAudio();
    assert.equal(audio.shouldSendPacket("voice", true, false), true);
    audio.muted = true;
    assert.equal(audio.shouldSendPacket("voice", true, false), false);
    assert.equal(audio.shouldSendPacket("ptt", true, true), false);
});
test("speaker identity survives main-thread delivery", () => {
    const audio = new SvgAudio();
    let delivered;
    audio.audioWorkletNode = { port: { postMessage: data => { delivered = data; } } };
    audio.playAudio({ samples: new Float32Array(1920), channels: 2, streamId: "alice" });
    assert.equal(delivered.buffer.streamId, "alice");
});

test("HTTPS playback preserves every sample across 300ms delivery bursts", () => {
    const p = processor();
    p.port.onmessage({data:{type:"buffer-ms",milliseconds:300}});
    let played = 0;
    // 300ms of speech arrives at once, followed by another batch 300ms later.
    for (let batch = 0; batch < 4; batch++) {
        enqueue(p, "alice", 0.25, 14400);
        for (let i = 0; i < 112; i++) played += render(p)[0].filter(v => v !== 0).length;
    }
    for(let i=0;i<120;i++) played += render(p)[0].filter(v=>v!==0).length;
    assert.equal(played, 4*14400);
});
test("HTTPS plays a short word even when the jitter cushion never fills", () => {
    const p = processor();
    p.port.onmessage({data:{type:"buffer-ms",milliseconds:300}});
    enqueue(p,"alice",0.25,960);
    let played=0;
    for(let i=0;i<130;i++) played += render(p)[0].filter(v=>v!==0).length;
    assert.equal(played,960);
});
test("microphone hangover retains quiet word endings", () => {
    let Processor;
    const context=vm.createContext({Float32Array,Math,sampleRate:48000,
      AudioWorkletProcessor:class {constructor(){this.port={postMessage:data=>this.last=data};}},
      registerProcessor:(_,p)=>Processor=p});
    vm.runInContext(readFileSync(new URL("../core/web/js/audio/microphone.js",import.meta.url),"utf8"),context);
    const p=new Processor();
    p.process([[new Float32Array(128).fill(0.1)]]);
    for(let i=0;i<75;i++)p.process([[new Float32Array(128)]]);
    assert.equal(p.last.speech,true);
    for(let i=0;i<25;i++)p.process([[new Float32Array(128)]]);
    assert.equal(p.last.speech,false);
});
