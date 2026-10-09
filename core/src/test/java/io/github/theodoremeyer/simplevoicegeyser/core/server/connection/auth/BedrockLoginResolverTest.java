package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth;

import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockLoginResolverTest {
    @Test void acceptsGamertagWithoutAnyServerPrefix() {
        for (String prefix : List.of(".", "BR_", "")) {
            var p = new Player(prefix + "ExactName", true);
            assertEquals(p.id, BedrockLoginResolver.resolve("ExactName", List.of(p), id -> "ExactName"));
        }
    }
    @Test void retainsExistingPrefixedLogin() {
        var p = new Player(".ExactName", true);
        assertEquals(p.id, BedrockLoginResolver.resolve(".ExactName", List.of(p), id -> "ExactName"));
    }
    @Test void rejectsIncorrectCapitalizationAndOfflinePlayers() {
        var p = new Player(".ExactName", true);
        assertNull(BedrockLoginResolver.resolve("exactname", List.of(p), id -> "ExactName"));
        assertNull(BedrockLoginResolver.resolve("ExactName", List.of(new Player(".ExactName", false)), id -> "ExactName"));
    }
    @Test void javaAccountCannotShadowBedrockGamertag() {
        var java = new Player("ExactName", true);
        var bedrock = new Player(".ExactName", true);
        assertEquals(bedrock.id, BedrockLoginResolver.resolve("ExactName", List.of(java, bedrock),
                id -> id.equals(bedrock.id) ? "ExactName" : null));
        assertNull(BedrockLoginResolver.resolve("ExactName", List.of(java), id -> null));
    }
    @Test void linkedAccountAndSpacesUseOriginalGamertag() {
        var p = new Player("LinkedJavaName", true);
        assertEquals(p.id, BedrockLoginResolver.resolve("My Gamertag", List.of(p), id -> "My Gamertag"));
    }
    @Test void ambiguousMatchesFailClosed() {
        var a = new Player(".Name", true);
        var b = new Player("Name", true);
        assertNull(BedrockLoginResolver.resolve("Name", List.of(a,b), id -> id.equals(a.id) ? "Name" : "Other"));
    }
    static class Player extends SvgPlayer {
        final UUID id = UUID.randomUUID(); final String name; final boolean online;
        Player(String name, boolean online) { this.name=name; this.online=online; }
        public UUID getUniqueId() { return id; }
        public String getName() { return name; }
        public boolean isOnline() { return online; }
        public boolean hasPermission(String p) { return true; }
        public Object getPlayer() { return null; }
        public void chat(String m) {}
        public void sendMessage(String m) {}
    }
}
