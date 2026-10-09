# CrossTalk: installation

This package adds browser voice chat to a Paper server with Simple Voice Chat.
It is a customized MIT-licensed build of SimpleVoice-Geyser, not a Bedrock add-on.

## Server owner: one-time setup

1. Stop your Paper server and back up its plugins/configuration.
2. Put `CrossTalk-Paper-0.1.4-crosstalk.8.jar` in `plugins/`.
   If SimpleVoice-Geyser is already installed, replace it: do not run both.
3. Keep **Simple Voice Chat's Bukkit/Paper plugin** on the server, plus your
   existing Geyser/Floodgate setup. Your `voicechat-fabric-1.21.11-2.6.22.jar`
   belongs in the Java player's Fabric client `mods/` directory.
   Fabric API is not a Paper plugin and does not add browser voice by itself.
4. Start the server once. CrossTalk creates `plugins/SimpleVoice-Geyser/` and
   serves its website on `127.0.0.1:8080` by default.
5. Connect a public **HTTPS** address to this service. On a VPS with Caddy on
   the same machine, use `deploy/Caddyfile.example` and replace the domain.
   Caddy handles both the page and its WebSocket connection.
   On a managed Minecraft host, ask for an extra TCP port and an HTTPS reverse
   proxy that supports WebSockets. Follow the host's bind-address requirements;
   container hosts commonly need `server.bind-address: 0.0.0.0` and their
   allocated port. The unencrypted origin must remain behind the proxy on a
   private/trusted connection or tunnel. Do not use a public HTTP address.
6. Give players your HTTPS URL. No separate website upload is needed.

The host name and domain are needed to finish step 5 for your particular server.
GitHub stores/builds the code; GitHub Pages cannot run this Java voice server.

## Players

Browser voice chat is for **Bedrock players only**. Java players must use the
Simple Voice Chat mod in-game. The server verifies the account through Floodgate
or Geyser; if verification is unavailable, browser login is blocked. The legacy
`client.requireBedrock` setting can no longer disable this restriction.

1. Join your Minecraft server.
2. Open the HTTPS voice page. Enter your exact Bedrock username without the prefix.
3. Press **Send code to Minecraft** and read the private message in Minecraft chat.
4. Enter the six-digit code on the website and press **Start call**. Allow the microphone.
   Codes expire after two minutes and work once. No saved password is needed.
   Keep Minecraft and the voice page open. Never share your confirmation code.

Java players with Simple Voice Chat installed use the in-game mod normally.
Browser users must stay logged into Minecraft. Leaving/disconnecting stops
the browser microphone. A username alone cannot authorize a voice session.

## Groups and invitations

After starting a call, use **Voice groups** on the website. Choose an available
group and press **Join group**, or open **Create a group**. A group password is
optional and separate from the Minecraft login code. Use **Leave group** to
return to proximity chat.

Members can select an online player and press **Send group invite**. Invitations
appear on the recipient's website and in Minecraft chat, expire after five
minutes, and can only be accepted by that recipient. An invitation grants entry
to that group without disclosing its password. Java players can invite Bedrock
players with `/voicechat invite <playername>`; Bedrock recipients accept on the
website. Java recipients of website invites can type the exact
`/voicechat join <group-id>` command from the invitation in Minecraft.

Group lists, invitations, and player faces refresh every second. Groups use Simple Voice
Chat's existing audio routing. Server settings and group permissions still
apply. A failed group action leaves the voice call connected.

### Player faces and speech

The website shows your group members and players within the server's voice
range. Faces gain a white outline while their audio is playing; your own face
lights up when your microphone detects speech. Heads appear and disappear as
players enter or leave range, change groups, or leave Minecraft. Nearby players
may still be silent because of group settings or because their voice is offline.
The website uses the skin already supplied by the Minecraft player profile;
when a proxy does not forward a skin, a default face appears.

The microphone remains open while unmuted to preserve quiet syllables. Use
**Mute microphone** to stop transmitting. Brief delivery gaps no longer add a
second full playback-buffer delay. Browser audio uses source-tagged PCM so
multiple speakers keep separate faces and can be heard together.

## First live test

Use one Java player with the mod and one Bedrock player with the browser.
Stand together in the same world; speak in each direction. Move beyond the
configured Simple Voice Chat range and confirm proximity audio fades/stops.
Test mute, End call, leaving Minecraft, and two simultaneous speakers.
Use headphones to avoid echo. Leave groups with `/svg lgroup` if testing
proximity; group settings can intentionally change who hears whom.

## If something does not connect

- Website unreachable: verify the extra TCP port, bind address, DNS and proxy.
- Page works but call fails: check the proxy forwards `/ws` or `/api/voice/`. CrossTalk falls back to HTTPS when WebSocket upgrades are unavailable.
- Microphone blocked: use HTTPS and allow this site's microphone permission.
- Invalid login: stay online from Bedrock and match your username's capitalization.
  Request a fresh code and read your private Minecraft chat. Expired or used codes cannot reconnect.
- Voice plugin unavailable: install the Bukkit/Paper build of Simple Voice Chat
  on Paper. The attached Fabric jar is for Java clients.
- Browser dies in the background: keep the phone unlocked with this page open,
  or use a second device. Mobile operating systems may suspend background audio.
- Java players cannot hear each other either: fix Simple Voice Chat's UDP port
  first. It must be separate from Geyser's UDP port.

Automated tests cover audio delivery, login, and groups. Verify real speech with
two Minecraft clients after updating; network conditions can still affect quality.


### Minekeep and other proxy hosts
If Bedrock players are online but code requests say to join from Bedrock, the host may run Geyser/Floodgate on its proxy without forwarding the Floodgate API data to Paper. For a trusted, authenticated proxy with the backend isolated from direct connections, set:

```yaml
client:
  trusted-proxy-bedrock:
    enabled: true
    prefix: "."
```

This requires Bungee player-info forwarding, an online player with a Floodgate XUID UUID, and the configured server prefix. Players still enter their username without the prefix and must prove ownership with the private in-game code. Ordinary Java UUIDs are rejected. Leave this disabled on standalone or publicly accessible offline-mode backends. Linked Java UUIDs need the normal Floodgate API data forwarding. See [Floodgate proxy setup](https://geysermc.org/wiki/floodgate/api/).
