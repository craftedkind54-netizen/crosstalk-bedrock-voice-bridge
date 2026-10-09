package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedrockAccessTest {
    @Test
    void verifiedBedrockPlayerPassesEditionCheck() {
        assertTrue(ConnectionAuthenticator.requireBedrock(Boolean.TRUE).success());
    }

    @Test
    void javaPlayerIsDeniedAndDirectedToInGameMod() {
        AuthResponse result = ConnectionAuthenticator.requireBedrock(Boolean.FALSE);
        assertFalse(result.success());
        assertTrue(result.message().contains("Java players must use the Simple Voice Chat mod"));
        assertNull(result.uuid());
        assertNull(result.player());
    }

    @Test
    void missingEditionVerificationFailsClosed() {
        AuthResponse result = ConnectionAuthenticator.requireBedrock(null);
        assertFalse(result.success());
        assertTrue(result.message().contains("enable Floodgate or Geyser"));
        assertNull(result.uuid());
        assertNull(result.player());
    }
}
