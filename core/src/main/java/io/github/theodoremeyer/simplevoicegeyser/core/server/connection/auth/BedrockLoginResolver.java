package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth;

import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Function;

/** Resolves a case-sensitive gamertag to a verified, online Bedrock account. */
final class BedrockLoginResolver {
    private BedrockLoginResolver() {}

    static UUID resolve(String username, Collection<SvgPlayer> players,
                        Function<UUID, String> bedrockUsername) {
        if (username == null || username.isBlank()) return null;
        UUID match = null;
        for (SvgPlayer player : players) {
            if (!player.isOnline()) continue;
            UUID uuid = player.getUniqueId();
            String gamertag = bedrockUsername.apply(uuid);
            if (gamertag == null) continue;
            // Accept the original gamertag; retain the server name for existing bookmarks.
            if (!username.equals(gamertag) && !username.equals(player.getName())) continue;
            if (match != null && !match.equals(uuid)) return null;
            match = uuid;
        }
        return match;
    }
}
