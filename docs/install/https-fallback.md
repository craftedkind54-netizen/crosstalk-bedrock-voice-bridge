# HTTPS fallback (CrossTalk .2)

CrossTalk tries a WebSocket first. If its handshake fails or takes more than
four seconds, the browser switches to same-origin HTTPS requests under
`/api/voice/`. Keep forwarding the entire site to the plugin, including this path.
No additional port or separate website is needed.

The fallback uses the same in-game account, voice password, permissions,
compatibility checks and proximity audio as WebSocket calls. It keeps a random
session token in browser memory, never in a URL or local storage. The server
expires abandoned sessions after 15 seconds. Ending a call, leaving Minecraft,
or replacing a session releases the voice sender and listener.

Audio travels as short PCM batches. Queues are bounded and stale audio is
discarded; the fallback can have more latency and HTTPS overhead than WebSockets.
Test two-way audio and multiple speakers on the actual host before relying on it.

## Upgrade

1. Stop the server and keep a copy of the previous plugin.
2. Replace the CrossTalk jar with `CrossTalk-Paper-0.1.4-crosstalk.2.jar`.
   Do not load both versions.
3. Start the server and hard-refresh the voice page. If you customized
   `plugins/SimpleVoice-Geyser/web`, back up the overrides and update the modified
   page/scripts to the new bundled versions; overrides are intentionally preserved.
4. A GET to `/api/voice/` should report `https-poll-v1`. This checks routing only,
   not account authentication or audio.
5. Join in-game, sign in on the voice page, and test speaking both ways, mute,
   End call, proximity, and leaving Minecraft.

Rollback: stop the server, restore the old jar and backed-up web overrides, then
restart. The account store and voice passwords are unchanged.
