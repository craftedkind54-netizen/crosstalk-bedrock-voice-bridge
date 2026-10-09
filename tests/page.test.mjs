import test from "node:test";
import assert from "node:assert/strict";

test("full browser call flow handles denied mic, authentication, mute, and end without leaking capture", async () => {
    const elements = new Map();
    function element(id) {
        if (!elements.has(id)) elements.set(id, {
            value: "", hidden: false, disabled: false, textContent: "", listeners: {},
            classList: { toggle() {}, remove() {} },
            addEventListener(type, fn) { this.listeners[type] = fn; },
            setAttribute() {}, replaceChildren() {}, append() {},
        });
        return elements.get(id);
    }
    globalThis.document = { getElementById: element, body: element("body"), querySelector: () => ({content: "test"}), createElement: () => ({}) };
    class AudioContext {
        constructor() { this.sampleRate = 48000; this.audioWorklet = { addModule: async () => {} }; this.destination = {}; }
        async resume() {}
        createMediaStreamSource() { return { connect() {}, disconnect() {} }; }
        createGain() { return { gain: {}, connect() {}, disconnect() {} }; }
    }
    globalThis.AudioWorkletNode = class { constructor() { this.port = { postMessage() {} }; } connect() {} disconnect() {} };
    globalThis.window = { AudioContext, isSecureContext: true, location: { href: "https://voice.example.com/" }, addEventListener() {} };
    globalThis.location = { protocol: "https:" };
    let allowed = false;
    const track = { stopped: false, stop() { this.stopped = true; }, addEventListener() {} };
    Object.defineProperty(globalThis, "navigator", { configurable: true, value: { mediaDevices: {
        enumerateDevices: async () => [],
        getUserMedia: async () => {
            if (!allowed) throw Object.assign(new Error("Permission denied"), {name: "NotAllowedError"});
            track.stopped = false;
            return { getTracks: () => [track], getAudioTracks: () => [track] };
        }
    } } });
    let ws;
    globalThis.WebSocket = class {
        static OPEN = 1;
        constructor() { ws = this; this.readyState = 0; this.bufferedAmount = 0; }
        send() {}
        close() { this.readyState = 3; }
    };
    await import("../core/web/js/v2.js");
    element("username").value = ".TestPlayer";
    element("code").value = "123456";
    let requests = 0;
    globalThis.fetch = async (url, options) => {
        requests++;
        assert.match(String(url), /api\/voice\/code$/);
        assert.deepEqual(JSON.parse(options.body), {username: ".TestPlayer"});
        return {ok:true, json:async()=>({message:"Code sent privately in Minecraft."})};
    };
    await element("send-code").listeners.click();
    assert.equal(requests, 1);
    assert.equal(ws, undefined);
    assert.match(element("status").textContent, /Code sent/);
    element("code").value="123456";
    const submit = () => element("call-form").listeners.submit({ preventDefault() {} });
    await submit();
    assert.match(element("error").textContent, /Microphone access was blocked/);
    assert.equal(element("start-call").disabled, false);
    assert.equal(ws, undefined);
    allowed = true;
    await submit();
    ws.readyState = 1;
    ws.onopen();
    assert.equal(element("connection-pill").textContent, "Offline");
    await ws.onmessage({ data: JSON.stringify({ type: "status", message: "Connected as .TestPlayer." }) });
    assert.equal(element("connection-pill").textContent, "In call");
    assert.equal(element("code").value, "");
    element("mute").listeners.click();
    assert.equal(element("mic-text").textContent, "Microphone muted");
    element("end-call").listeners.click();
    assert.equal(track.stopped, true);
    assert.equal(element("connection-pill").textContent, "Offline");
    assert.equal(element("call-controls").hidden, true);
});
