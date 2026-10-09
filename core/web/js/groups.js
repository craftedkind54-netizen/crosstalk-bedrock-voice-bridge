export function setupGroups(socket, document) {
    const el = id => document.getElementById(id);
    let timer;
    let active = false;
    let lastRequest = 0;
    function send(action, fields = {}) {
        if (!active || !socket.isConnected()) return;
        lastRequest = Date.now();
        socket.ws.send(JSON.stringify({ type: "groups", action, ...fields }));
        if (action !== "list") el("group-message").textContent = "Updating…";
    }
    function options(id, items, empty) {
        const select = el(id), previous = select.value;
        select.replaceChildren();
        const placeholder = document.createElement("option");
        placeholder.value = ""; placeholder.textContent = empty; select.append(placeholder);
        for (const item of items) {
            const option = document.createElement("option");
            option.value = item.id; option.textContent = item.name + (item.locked ? " · password" : "");
            select.append(option);
        }
        select.value = items.some(i => i.id === previous) ? previous : "";
    }
    socket.addEventListener("statusChange", status => {
        active = status.connected;
        clearInterval(timer);
        el("group-message").textContent = "";
        el("group-invites").replaceChildren();
        el("current-group").textContent = "Proximity chat";
        if (active) {
            send("list");
            timer = setInterval(() => { if (Date.now() - lastRequest > 4000) send("list"); }, 5000);
        }
    }, true);
    socket.addEventListener("message", message => {
        if (!active || message.packetType !== "groups") return;
        const data = message.payload;
        if (data.error) { el("group-message").textContent = data.error; return; }
        el("current-group").textContent = data.current ? "Group: " + data.current.name : "Proximity chat";
        el("leave-group").disabled = !data.current;
        el("invite-player").disabled = !data.current;
        options("group-list", data.groups || [], "Choose a group");
        options("player-list", data.players || [], "Choose a player");
        const invites = el("group-invites");
        invites.replaceChildren();
        for (const invite of data.invites || []) {
            const row = document.createElement("div"), label = document.createElement("p");
            row.className = "group-invite";
            label.textContent = invite.from + " invited you to " + invite.name;
            row.append(label);
            for (const action of ["accept", "decline"]) {
                const button = document.createElement("button");
                button.type = "button"; button.textContent = action === "accept" ? "Accept invite" : "Decline";
                button.addEventListener("click", () => send(action, { id: invite.id })); row.append(button);
            }
            invites.append(row);
        }
        if (data.message) el("group-message").textContent = data.message;
    }, true);
    el("refresh-groups").addEventListener("click", () => send("list"));
    el("join-group").addEventListener("click", () => {
        send("join", { id: el("group-list").value, password: el("group-password").value });
        el("group-password").value = "";
    });
    el("leave-group").addEventListener("click", () => send("leave"));
    el("invite-player").addEventListener("click", () => send("invite", { player: el("player-list").value }));
    el("create-group").addEventListener("click", () => {
        send("create", { name: el("new-group-name").value, password: el("new-group-password").value });
        el("new-group-password").value = "";
    });
    return () => { active = false; clearInterval(timer); };
}
