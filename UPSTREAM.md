# Upstream provenance

- Repository: https://github.com/TheodoreMeyer/SimpleVoice-Geyser
- Source archive: https://codeload.github.com/TheodoreMeyer/SimpleVoice-Geyser/zip/refs/heads/master
- Retrieved: 2026-10-05
- Archive SHA-256: `5925dd416212ae5660b000ffffcab107168195f08b3140cfa979068a65c61d3e`
- Upstream declared version: 0.1.4
- Fork version: 0.1.4-crosstalk.1
- Original license: MIT, copyright TheodoreMeyer 2025–2026.

The archive was downloaded from a moving branch. Its SHA-256 identifies the
exact downloaded archive; no upstream commit identity is asserted here.

CrossTalk changes the default browser UI; gates microphone transmission and
connection status on authenticated join; stops capture on disconnect; adds
keepalives; uses built-in PCM transport without runtime CDN codec downloads;
isolates Opus decoding by speaker; adds source-tagged PCM frames and browser
mixing; bounds audio queues and stale work; handles audio sender registration
failure; defaults to proximity and loopback binding; and adds tests, Paper-only
build configuration, CI artifacts, and deployment instructions.

Upstream commands and plugin identity are retained. Upstream documentation in
`docs/` may describe features or defaults that differ from this customized
build. Follow the root README and START-HERE for this package.
