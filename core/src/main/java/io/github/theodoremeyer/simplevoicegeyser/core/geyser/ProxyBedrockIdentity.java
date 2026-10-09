package io.github.theodoremeyer.simplevoicegeyser.core.geyser;

import java.util.UUID;

/** Identity forwarded by an authenticated, isolated Floodgate proxy (not browser input). */
public final class ProxyBedrockIdentity {
    private ProxyBedrockIdentity() {}

    public static String username(UUID uuid, String serverName, boolean enabled,
                                  boolean forwardingEnabled, String prefix) {
        // Floodgate encodes the XUID in the low 64 bits; Java online/offline UUIDs
        // have nonzero high bits. Never grant access using a name prefix alone.
        if (!enabled || !forwardingEnabled || uuid == null
                || uuid.getMostSignificantBits() != 0 || uuid.getLeastSignificantBits() <= 0
                || prefix == null || prefix.isEmpty() || serverName == null
                || !serverName.startsWith(prefix) || serverName.length() <= prefix.length()) return null;
        return serverName.substring(prefix.length());
    }
}
