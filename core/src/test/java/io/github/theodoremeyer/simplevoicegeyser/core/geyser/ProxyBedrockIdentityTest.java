package io.github.theodoremeyer.simplevoicegeyser.core.geyser;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ProxyBedrockIdentityTest {
    final UUID bedrock = UUID.fromString("00000000-0000-0000-0009-01f657a5a7eb");
    @Test void acceptsProxyIdentityWithoutRequiringPrefixInBrowser() {
        assertEquals("kindCrafted", ProxyBedrockIdentity.username(bedrock, ".kindCrafted", true, true, "."));
    }
    @Test void requiresExplicitTrustAndForwarding() {
        assertNull(ProxyBedrockIdentity.username(bedrock, ".kindCrafted", false, true, "."));
        assertNull(ProxyBedrockIdentity.username(bedrock, ".kindCrafted", true, false, "."));
    }
    @Test void rejectsJavaAndOfflineUuidsEvenWithBedrockPrefix() {
        assertNull(ProxyBedrockIdentity.username(UUID.randomUUID(), ".kindCrafted", true, true, "."));
        assertNull(ProxyBedrockIdentity.username(UUID.nameUUIDFromBytes("OfflinePlayer:.kindCrafted".getBytes()), ".kindCrafted", true, true, "."));
    }
    @Test void requiresValidXuidAndExpectedPrefix() {
        assertNull(ProxyBedrockIdentity.username(new UUID(0, 0), ".Name", true, true, "."));
        assertNull(ProxyBedrockIdentity.username(bedrock, "Name", true, true, "."));
        assertNull(ProxyBedrockIdentity.username(bedrock, ".Name", true, true, ""));
        assertNull(ProxyBedrockIdentity.username(bedrock, ".", true, true, "."));
    }
}
