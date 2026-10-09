import test from "node:test";
import assert from "node:assert/strict";
import { SvgWebSocket } from "../core/web/js/websocket.js";
import { HttpVoiceSocket } from "../core/web/js/http-voice.js";

globalThis.location = { protocol: "https:", reload() {} };
globalThis.window = { location: { href: "https://voice.example.com/" }, isSecureContext: true, PROJECT_VERSION: "test", BUILD_ID: "test" };
class FakeSocket {
    static OPEN = 1;
    constructor(url) { this.url = url; this.readyState = 0; this.bufferedAmount = 0; this.sent = []; }
    send(data) { this.sent.push(data); }
    close() { this.readyState = 3; }
    open() { this.readyState = 1; this.onopen(); }
    message(data) { return this.onmessage({ data: typeof data === "object" && !(data instanceof ArrayBuffer) ? JSON.stringify(data) : data }); }
}
globalThis.WebSocket = FakeSocket;
function setup() {
    const audio = { onMicData(handler) { this.send = handler; }, stopMic() { this.stopped = true; }, resetAudioState() {}, getAudioRuntime: () => ({workletSupported: true}), playAudio(packet) { this.received = packet; } };
    const bridge = new SvgWebSocket(audio);
    bridge.initWebSocket();
    const status = [];
    bridge.connect(".BedrockPlayer", "test-only-password", event => status.push(event));
    bridge.stopReconnection();
    return { bridge, ws: bridge.ws, audio, status };
}

test("failed WebSocket upgrade falls back once and ignores stale socket callbacks", async () => {
    const requests = [];
    globalThis.fetch = async (url, options) => {
        requests.push({url: String(url), options});
        return {ok: false, json: async () => ({open:false, messages:[JSON.stringify({type:'error', message:'Invalid voice password'})]})};
    };
    const {bridge, ws, status} = setup();
    ws.onerror();
    assert.ok(bridge.ws instanceof HttpVoiceSocket);
    const fallback = bridge.ws;
    ws.onclose({code:1006});
    ws.onopen();
    assert.equal(bridge.ws, fallback);
    assert.equal(ws.sent.length, 0);
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(requests.length, 1);
    assert.match(requests[0].url, /\/api\/voice\/join$/);
    assert.equal(status.some(s => s.connected), false);
    bridge.disconnect();
});

test("opening a socket does not claim success or send microphone audio before authentication", async () => {
    const { bridge, ws, audio, status } = setup();
    ws.open();
    audio.send(new ArrayBuffer(1920));
    assert.equal(status.length, 0);
    assert.equal(bridge.isConnected(), false);
    assert.equal(ws.sent.length, 1);
    await ws.message({ type: "status", message: "Connected as .BedrockPlayer." });
    assert.equal(status[0].connected, true);
    assert.equal(bridge.isConnected(), true);
    audio.send(new ArrayBuffer(1920));
    assert.ok(ws.sent.at(-1) instanceof ArrayBuffer);
    ws.bufferedAmount = 30000;
    const count = ws.sent.length;
    audio.send(new ArrayBuffer(1920));
    assert.equal(ws.sent.length, count);
    bridge.disconnect();
});
test("failed authentication never emits connected", async () => {
    const { ws, bridge, status } = setup();
    ws.open();
    await ws.message({ type: "error", message: "Access Denied: Invalid username or password." });
    assert.equal(status.some(s => s.connected), false);
    bridge.disconnect();
});
test("stale socket close cannot disrupt a new connection", () => {
    const { bridge, ws: old, audio } = setup();
    bridge.connect(".BedrockPlayer", "test-only-password", () => {});
    old.onclose({ code: 1000 });
    assert.equal(audio.stopped, undefined);
    assert.notEqual(bridge.ws, old);
    bridge.disconnect();
});
test("disconnect turns off microphone and CrossTalk frames retain source and stereo PCM", async () => {
    const { ws, bridge, audio } = setup();
    ws.open();
    await ws.message({ type: "status", message: "Connected as .BedrockPlayer." });
    const frame = new ArrayBuffer(24);
    const view = new DataView(frame);
    view.setUint32(0, 0x43545031);
    view.setUint8(19, 1);
    view.setInt16(20, 16384, true);
    view.setInt16(22, -16384, true);
    await ws.message(frame);
    assert.equal(audio.received.streamId, "00000000000000000000000000000001");
    assert.deepEqual([...audio.received.samples], [0.5, -0.5]);
    ws.onclose({ code: 1000, reason: "Disconnected" });
    assert.equal(audio.stopped, true);
    assert.equal(bridge.isConnected(), false);
    bridge.disconnect();
});
