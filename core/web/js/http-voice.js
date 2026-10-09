/** WebSocket-shaped HTTPS channel for proxies that cannot complete an upgrade. */
export class HttpVoiceSocket {
    constructor(wsUrl) {
        const url = new URL(wsUrl);
        url.protocol = url.protocol === "wss:" ? "https:" : "http:";
        url.pathname = url.pathname.replace(/\/ws$/, "/api/voice/");
        this.base = url.href;
        this.readyState = 0;
        this.bufferedAmount = 0;
        this.queue = [];
        this.controls = [];
        this.token = null;
        this.joining = false;
        queueMicrotask(() => {
            if (this.readyState !== 0) return;
            this.readyState = 1;
            this.onopen?.();
        });
    }

    send(data) {
        if (this.readyState !== 1) return;
        if (typeof data === "string") {
            const packet = JSON.parse(data);
            if (packet.type === "join" && !this.joining) {
                this.joining = true;
                void this.join(packet);
            }
            if (packet.type === "groups" && this.token && this.controls.length < 4) this.controls.push(packet);
            // HTTPS exchanges provide keepalive; this transport always uses PCM.
            return;
        }
        if (!this.token) return;
        const bytes = data instanceof ArrayBuffer ? new Uint8Array(data) : new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
        if (bytes.length !== 1920) return;
        this.queue.push({ time: Date.now(), data: btoa(String.fromCharCode(...bytes)) });
        if (this.queue.length > 40) this.queue.shift();
        this.bufferedAmount = this.queue.length * 1920;
    }

    async request(path, body) {
        this.controller = new AbortController();
        const controller = this.controller;
        const timer = setTimeout(() => controller.abort(), 8000);
        try {
            const response = await fetch(new URL(path, this.base), {
                method: "POST", credentials: "omit", cache: "no-store", signal: controller.signal,
                headers: { "Content-Type": "application/json", ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}) },
                body: JSON.stringify(body)
            });
            const result = await response.json();
            if (!response.ok && !result.messages?.length) {
                throw new Error(result.error || "The voice server could not accept the connection.");
            }
            return result;
        } finally {
            clearTimeout(timer);
            if (this.controller === controller) this.controller = null;
        }
    }

    async deliver(result) {
        for (const text of result.messages || []) {
            if (this.readyState !== 1) return;
            await this.onmessage?.({ data: text });
        }
        for (const frame of result.audio || []) {
            if (this.readyState !== 1) return;
            const bytes = Uint8Array.from(atob(frame), ch => ch.charCodeAt(0));
            await this.onmessage?.({ data: bytes.buffer });
        }
        if (result.open === false && this.readyState === 1) this.fail(result.reason || "Voice session ended.", result.code || 1000);
    }

    async join(packet) {
        try {
            const result = await this.request("join", packet);
            if (this.readyState !== 1) {
                if (result.token) this.release(result.token);
                return;
            }
            this.token = result.token || null;
            await this.deliver(result);
            if (this.readyState !== 1) return;
            if (!this.token) throw new Error("The voice server did not authorize the call.");
            void this.poll();
        } catch (error) { this.fail(error.message || "HTTPS voice connection failed."); }
    }

    async poll() {
        if (this.readyState !== 1 || !this.token) return;
        const started = Date.now();
        const audio = this.queue.splice(0).filter(frame => Date.now() - frame.time < 1000).map(frame => frame.data);
        this.bufferedAmount = 0;
        try {
            await this.deliver(await this.request("exchange", { audio, controls: this.controls.splice(0) }));
            if (this.readyState === 1) this.pollTimer = setTimeout(() => void this.poll(), Math.max(0, 20 - (Date.now() - started)));
        } catch (error) { this.fail(error.name === "AbortError" ? "Voice connection timed out. Start a new call." : error.message); }
    }

    release(token) {
        void fetch(new URL("close", this.base), {
            method: "POST", credentials: "omit", keepalive: true,
            headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` }, body: "{}"
        }).catch(() => {});
    }

    fail(reason, code = 1006) {
        if (this.readyState === 3) return;
        this.close();
        this.onclose?.({ code, reason });
    }

    close() {
        if (this.readyState === 3) return;
        this.readyState = 3;
        clearTimeout(this.pollTimer);
        this.controller?.abort();
        this.queue = [];
        this.bufferedAmount = 0;
        if (this.token) this.release(this.token);
        this.token = null;
    }
}
