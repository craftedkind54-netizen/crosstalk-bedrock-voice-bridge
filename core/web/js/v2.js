import { SvgAudio } from "./audio/audio.js";
import { SvgWebSocket } from "./websocket.js?crosstalk=2";

window.PROJECT_VERSION = document.querySelector('meta[name="project-version"]').content;
window.BUILD_ID = document.querySelector('meta[name="build-id"]').content;
const el = id => document.getElementById(id);
const audio = new SvgAudio();
const socket = new SvgWebSocket(audio);
let generation = 0;
let timeout;
let heartbeat;
let ready = false;
let busy = false;
let joined = false;
audio.setMicIndicator(el("mic-light"));
socket.initWebSocket();

function showError(message) {
    el("error").textContent = message;
    el("error").hidden = !message;
}

function render(connected) {
    joined = connected;
    document.body.classList.toggle("connected", connected);
    el("call-form").hidden = connected;
    el("call-controls").hidden = !connected;
    el("connection-pill").textContent = connected ? "In call" : "Offline";
    el("connection-pill").classList.toggle("online", connected);
    el("call-heading").textContent = connected ? "You're in." : "Come say hello.";
    el("call-subtitle").textContent = connected ? "Stay in-game and keep this page open." : "Your next adventure sounds better together.";
    el("start-call").disabled = busy;
    el("start-call").textContent = busy ? "Connecting…" : "Start call ↗";
}

function end(message = "Call ended. Your microphone is off.") {
    generation++;
    clearTimeout(timeout);
    clearInterval(heartbeat);
    socket.disconnect();
    socket.stopReconnection();
    audio.stopMic();
    audio.resetAudioState();
    busy = false;
    render(false);
    el("status").textContent = message;
}

socket.addEventListener("statusChange", status => {
    if (status.connected) {
        clearTimeout(timeout);
        clearInterval(heartbeat);
        heartbeat = setInterval(() => {
            if (socket.isConnected()) socket.ws.send(JSON.stringify({ type: "ping" }));
        }, 25000);
        busy = false;
        render(true);
        el("password").value = "";
        el("status").textContent = `Connected as ${status.username}. Talk to nearby players.`;
    } else if (joined || busy) {
        end("Disconnected. Your microphone is off. Start a new call to reconnect.");
        if (status.reason) showError(status.reason);
    }
}, true);

socket.addEventListener("message", data => {
    if (data.type === "json" && (data.packetType === "error" || data.fatalAuthError)) {
        end("Could not join. Check the message below, then try again.");
        showError(data.msg || "Check that you are online and that your voice password is correct.");
    }
}, true);

el("call-form").addEventListener("submit", async event => {
    event.preventDefault();
    if (busy || joined) return;
    showError("");
    if (!window.isSecureContext) {
        showError("Open the HTTPS voice-chat address provided by your server owner. Your browser needs a secure page to use the microphone.");
        return;
    }
    const attempt = ++generation;
    busy = true;
    render(false);
    el("status").textContent = "Allow your microphone when your browser asks.";
    try {
        if (!ready) {
            // Begin resume in the click gesture, before waiting for worklet downloads.
            const init = audio.initAudio();
            const resume = audio.audioContext?.resume();
            const runtime = await init;
            await resume;
            if (!runtime.canCaptureMic || audio.audioContext.sampleRate !== 48000) {
                throw new Error("This browser cannot start voice chat. Try an updated Chrome, Edge, Firefox, or Safari browser.");
            }
            ready = true;
        }
        await audio.startMic();
        if (attempt !== generation) { audio.stopMic(); return; }
        audio.microphoneStream.getAudioTracks().forEach(track => track.addEventListener("ended", () => {
            if (attempt === generation) end("Microphone disconnected. Reconnect it, then start a new call.");
        }));
        audio.muted = false;
        el("mute").textContent = "Mute microphone";
        el("mute").setAttribute("aria-pressed", "false");
        el("mic-text").textContent = "Microphone on";
        socket.connect(el("username").value.trim(), el("password").value, () => {});
        socket.stopReconnection();
        el("status").textContent = "Checking your in-game account…";
        timeout = setTimeout(() => {
            if (busy && attempt === generation) {
                end("Connection timed out. Your microphone is off.");
                showError("Make sure you are in-game. If this keeps happening, ask the server owner to check the voice bridge and HTTPS connection.");
            }
        }, 20000);
    } catch (error) {
        if (attempt !== generation) return;
        end("Call could not start. Your microphone is off.");
        const messages = {
            NotAllowedError: "Microphone access was blocked. Allow microphone access in your browser's site settings, then try again.",
            NotFoundError: "No microphone was found. Connect a headset or microphone, then try again.",
            NotReadableError: "Your microphone is busy or unavailable. Check your device settings, then try again."
        };
        showError(messages[error.name] || error.message || "Voice chat could not start.");
    }
});

el("mute").addEventListener("click", () => {
    const muted = audio.toggleMute();
    el("mute").textContent = muted ? "Unmute microphone" : "Mute microphone";
    el("mute").setAttribute("aria-pressed", String(muted));
    el("mic-text").textContent = muted ? "Microphone muted" : "Microphone on";
});
el("end-call").addEventListener("click", () => end());
window.addEventListener("pagehide", () => end());
if (!window.isSecureContext) showError("Use your server's HTTPS voice-chat address to enable your microphone.");
