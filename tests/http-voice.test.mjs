import test from 'node:test';
import assert from 'node:assert/strict';
import { HttpVoiceSocket } from '../core/web/js/http-voice.js';
const tick = () => new Promise(resolve => setImmediate(resolve));
const response = (body, ok = true) => ({ ok, json: async () => body });

test('HTTPS authenticates before sending audio; batches audio, preserves binary replies, and closes with bearer token', async () => {
    const calls = [];
    let completeExchange;
    globalThis.fetch = async (url, options) => {
        calls.push({url: String(url), ...options});
        if (String(url).endsWith('/join')) return response({ token: 'test-token', open: true, messages: ['{"type":"status","message":"Connected as Test."}'] });
        if (String(url).endsWith('/close')) return response({open:false});
        return new Promise(resolve => { completeExchange = resolve; });
    };
    const socket = new HttpVoiceSocket('wss://voice.example.com/prefix/ws');
    const messages = [];
    socket.onmessage = event => messages.push(event.data);
    await tick();
    socket.send(new ArrayBuffer(1920));
    assert.equal(socket.queue.length, 0);
    socket.send(JSON.stringify({type:'join', username:'Test', password:'test-only'}));
    await tick();
    assert.equal(calls[0].url, 'https://voice.example.com/prefix/api/voice/join');
    assert.equal(calls[1].headers.Authorization, 'Bearer test-token');
    assert.ok(!calls[1].body.includes('test-only'));
    for (let i = 0; i < 50; i++) socket.send(new ArrayBuffer(1920));
    assert.equal(socket.queue.length, 40);
    completeExchange(response({open:true, audio:[btoa('\x01\x02')]}));
    await tick();
    assert.deepEqual([...new Uint8Array(messages.at(-1))], [1,2]);
    socket.close();
    assert.equal(socket.queue.length, 0);
    assert.equal(calls.at(-1).url, 'https://voice.example.com/prefix/api/voice/close');
    assert.equal(calls.at(-1).headers.Authorization, 'Bearer test-token');
    assert.equal(socket.token, null);
});

test('HTTPS auth rejection exposes server message and never starts audio exchange', async () => {
    const calls = [];
    globalThis.fetch = async url => {
        calls.push(String(url));
        return response({open:true, messages:['{"type":"error","message":"Invalid voice password"}']}, false);
    };
    const socket = new HttpVoiceSocket('wss://voice.example.com/ws');
    const messages = [];
    socket.onmessage = event => messages.push(event.data);
    await tick();
    socket.send(JSON.stringify({type:'join'}));
    await tick();
    assert.match(messages[0], /Invalid voice password/);
    assert.equal(socket.readyState, 3);
    assert.equal(calls.length, 1);
});

test('a join completing after End call releases its token and cannot revive the call', async () => {
    let finish;
    const calls = [];
    globalThis.fetch = async url => {
        calls.push(String(url));
        if (String(url).endsWith('/join')) return new Promise(resolve => { finish = resolve; });
        return response({});
    };
    const socket = new HttpVoiceSocket('wss://voice.example.com/ws');
    let delivered = false;
    socket.onmessage = () => { delivered = true; };
    await tick();
    socket.send(JSON.stringify({type:'join'}));
    socket.close();
    finish(response({token:'late-token', messages:['connected'], open:true}));
    await tick();
    assert.equal(delivered, false);
    assert.ok(calls.at(-1).endsWith('/close'));
    assert.equal(socket.readyState, 3);
});

test('a 350ms round trip preserves all microphone frames and adds no fixed 60ms delay', async () => {
    const realNow=Date.now, realTimeout=globalThis.setTimeout, realClear=globalThis.clearTimeout;
    let now=0, nextDelay, pending;
    Date.now=()=>now;
    globalThis.setTimeout=(fn,ms)=>{ if(ms!==8000)nextDelay=ms; return 1; };
    globalThis.clearTimeout=()=>{};
    const bodies=[];
    globalThis.fetch=async (url,options)=>{
        if(String(url).endsWith('/close'))return response({});
        bodies.push(JSON.parse(options.body));
        return new Promise(resolve=>pending=resolve);
    };
    const socket=new HttpVoiceSocket('wss://voice.example.com/ws');
    try {
        await tick(); socket.token='test-token';
        const first=socket.poll();
        for(let i=0;i<17;i++){now=i*20;socket.send(new ArrayBuffer(1920));}
        now=350;
        pending(response({open:true}));
        await first;
        assert.equal(nextDelay,0);
        const second=socket.poll();
        assert.equal(bodies[1].audio.length,17);
        pending(response({open:true})); await second;
    } finally {
        socket.close(); Date.now=realNow; globalThis.setTimeout=realTimeout; globalThis.clearTimeout=realClear;
    }
});
