# CrossTalk — Bedrock ↔ Java voice bridge

A Paper server plugin with an embedded browser voice page. Java players keep
using Simple Voice Chat. Bedrock players join the same voice system from a
browser: username, an in-game voice password, **Start call**.

**Status: compiled development build, with automated tests. Not yet verified
in a live Java/Bedrock call on the target server.**

[Installation and player instructions](START-HERE.md)

## What is included

- A Paper plugin, including its website; no separate web application server.
- Proximity routing through Simple Voice Chat's player audio sender/listener APIs.
- Authentication tied to an online Minecraft account and an in-game password.
- Start call, mute, End call, and actionable connection/microphone errors.
- Independent speaker decoding and browser mixing for overlapping voices.
- Bounded browser buffers, bounded server audio queue, and stale audio dropping.
- No browser audio decoder CDN dependency; PCM is carried over WebSockets.
- GitHub Actions to test the code and produce a downloadable plugin artifact.
- An example Caddy HTTPS configuration.

## Requirements

- Paper (target: Minecraft 1.21.11), Java 21+.
- Simple Voice Chat **Bukkit/Paper** installed on the server (2.6.x API).
- Geyser/Floodgate or your existing working Java/Bedrock crossplay setup.
- An HTTPS reverse proxy with WebSocket support and access to the bridge TCP port.
- A microphone-capable browser on a computer or phone. Keep the page open.

The supplied `voicechat-fabric-1.21.11-2.6.22.jar` is a Fabric mod for Minecraft
1.21.11/Java 21+. Keep it on Java clients. It was inspected but is not modified
or redistributed by this project. It cannot be loaded as a Bedrock add-on or
as a Paper plugin. Fabric API is not required by this Paper bridge.

GitHub Pages alone cannot host the voice bridge: it serves static files and
does not run a Java plugin or WebSocket audio server.

## Build

Use JDK 21 and Node 22+:

```sh
npm test
bash gradlew :core:test :spigot:shadowJar --no-daemon
```

Windows: replace `bash gradlew` with `gradlew.bat`.

Output: `spigot/build/libs/CrossTalk-Paper-0.1.4-crosstalk.1.jar`.
No npm install is needed: the browser tests use Node's built-in test runner.
The Gradle wrapper downloads the required build dependencies.

For a **design-only** local preview: `npm run preview`, then open
`http://127.0.0.1:8794`. This preview has no Minecraft connection; actual voice
calls use the website served by the installed plugin.

## Server configuration

The Bukkit plugin identity remains `SimpleVoice-Geyser` for compatibility with
upstream commands/configuration. Install only one version at a time.
Configuration is in `plugins/SimpleVoice-Geyser/config.yml`.

Fresh installs bind to `127.0.0.1:8080` for a same-machine HTTPS reverse proxy.
Managed container hosts may require `0.0.0.0` plus a provider-allocated TCP port.
Keep the unencrypted origin private or behind a secure tunnel. Supply the page
and `/ws` through the same HTTPS origin. Do not ask players to disable browser
security checks. See [the Caddy example](deploy/Caddyfile.example).

The automatic default voice group is disabled, so proximity is the default.
In-game group commands remain available. Existing configs are preserved;
upgraders should set `server.group.default.enabled: false` explicitly if needed.
Automatic upstream update notifications are off for this customized build.

## Audio protocol and limits

Browser → server: 960 mono signed PCM16 LE samples at 48 kHz per frame (20ms).
The Simple Voice Chat native encoder converts these to Opus for Java clients.
Server → browser: `CTP1`, 16-byte source UUID in network byte order, followed
by interleaved stereo PCM16 LE at 48 kHz. Each source has a separate Opus decoder
and a bounded browser jitter buffer; sources are mixed during playback.

This trades bandwidth for no external browser codec dependency: approximately
96 KB/s upload while speaking and 192 KB/s download per audible speaker, before
protocol overhead. It is intended for small servers, with a 64-source cap per
browser. WebSockets run over TCP, so poor connections can cause dropouts.
There is no recording feature. The Minecraft server processes voice; this is
transport encryption through HTTPS/WSS, not end-to-end encryption.

Phones can suspend browser audio when locked or when Minecraft takes focus.
For console/mobile play, a second device is the most reliable arrangement.
Live device compatibility and latency must be checked with your actual players.

## Verification

Run `npm test` for microphone lifecycle, account verification gating,
backpressure, stereo/source framing, overlapping speakers, short utterances,
mute and disconnect cleanup. `:core:test` covers server compatibility checks,
audio listener registration, and PCM frame encoding.

Build and unit tests do not replace the live test in [START-HERE.md](START-HERE.md).
Server deployment, HTTPS/DNS configuration and a real two-client call are still
needed before calling this production-ready.

## Credits and source

Customized from [TheodoreMeyer/SimpleVoice-Geyser](https://github.com/TheodoreMeyer/SimpleVoice-Geyser),
under its MIT license. Original attribution is preserved in [LICENSE](LICENSE),
[NOTICE](NOTICE), and [Contributors.md](Contributors.md). See [UPSTREAM.md](UPSTREAM.md)
for the snapshot and changes. This is an independent fork, not an official
Simple Voice Chat, Geyser, Mojang, or Microsoft release.

The `fabric/` directory and upstream documentation are retained for provenance;
this fork builds/tests only `core` and `spigot`. Only the Paper jar is delivered.
