const key = id => String(id || "").replaceAll("-", "").toLowerCase();
export function skinUrl(value) {
    return /^https:\/\/textures\.minecraft\.net\/texture\/[a-f0-9]{32,64}$/i.test(value || "") ? value : "";
}
export function setupPlayers(socket, audio, document) {
    const el = id => document.getElementById(id);
    const cards = new Map(), speaking = new Set();
    let self = "", active = false;
    function clear() {
        cards.clear(); speaking.clear(); self = "";
        el("group-members").replaceChildren(); el("nearby-players").replaceChildren();
    }
    function refreshOutline(id) {
        const card = cards.get(id);
        if (!card) return;
        const talking = speaking.has(id);
        card.face.classList.toggle("speaking",talking);
        card.face.setAttribute("aria-label",card.name + (talking ? " is speaking" : " is quiet"));
        card.state.textContent = talking ? "Speaking" : card.connected ? "Voice connected" : "Voice offline";
    }
    function row(player, parent) {
        const id = key(player.id);
        let card = cards.get(id);
        if (!card) {
            const root = document.createElement("div"), face = document.createElement("div"), base = document.createElement("span"), hat = document.createElement("span"), name = document.createElement("span"), state = document.createElement("span");
            root.className = "voice-player"; face.className = "player-face"; base.className = "skin-base"; hat.className = "skin-hat"; name.className = "player-name"; state.className = "player-state";
            face.setAttribute("role","img");face.append(base,hat);root.append(face,name,state);
            card = {root,face,base,hat,label:name,state,skin:null}; cards.set(id,card);
        }
        card.name = player.name; card.connected = player.connected;
        card.label.textContent = player.name + (player.self ? " (you)" : "");
        card.root.title = player.edition || "Minecraft";
        const skin = skinUrl(player.skin);
        if (card.skin !== skin) {
            card.skin = skin;
            card.base.style.backgroundImage = skin ? 'url("' + skin + '")' : "";
            card.hat.style.backgroundImage = skin ? 'url("' + skin + '")' : "";
        }
        if (card.root.parentElement !== parent) parent.append(card.root);
        refreshOutline(id);
        return id;
    }
    socket.addEventListener("statusChange", status => { active = status.connected; clear(); },true);
    socket.addEventListener("message", event => {
        if (!active || event.packetType !== "groups" || event.payload.error) return;
        const data = event.payload;
        self = key(data.self?.id);
        const retained = new Set();
        const members = data.current ? (data.members || []) : (data.self ? [data.self] : []);
        for (const p of members) retained.add(row(p,el("group-members")));
        for (const p of data.nearby || []) retained.add(row(p,el("nearby-players")));
        for (const [id,card] of cards) if (!retained.has(id)) {card.root.remove();cards.delete(id);speaking.delete(id);}
        el("members-heading").textContent = data.current ? data.current.name + " · Group members" : "Your microphone";
        el("nearby-empty").hidden = (data.nearby || []).length > 0;
    },true);
    audio.onVoiceActivity(event => {
        if (event.type === "reset") { speaking.clear();for(const id of cards.keys())refreshOutline(id);return; }
        if (!active) return;
        const id = event.streamId === "self" ? self : key(event.streamId);
        if (!id) return;
        if (event.speaking) speaking.add(id);else speaking.delete(id);
        refreshOutline(id);
    });
    return () => {active=false;clear();};
}
