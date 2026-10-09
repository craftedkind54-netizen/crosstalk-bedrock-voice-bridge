package io.github.theodoremeyer.simplevoicegeyser.core.geyser;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import org.geysermc.cumulus.form.Form;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.api.GeyserApi;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Hook with Geyser MC
 */
public final class GeyserHook {

    private static FormHandler formHandler = null;

    /**
     * Block class from being made, as it is static util
     */
    private GeyserHook() {}

    /**
     * Whether Geyser is enabled in some form
     * @return geyser enabled
     */
    public static boolean isEnabled() {
        boolean enabled = isFloodgate();

        if (isGeyser()) enabled = true;

        return enabled;
    }

    /**
     * Is Geyser-Spigot Enabled
     * @return if its enabled
     */
    public static boolean isGeyser() {
        return SvgCore.getPlatform().isDependencyEnabled("Geyser-Spigot");
    }

    /**
     * Is Floodgate active
     * @return if its enabled
     */
    public static boolean isFloodgate() {
        return SvgCore.getPlatform().isDependencyEnabled("floodgate");
    }

    /**
     * is the Player Bedrock
     * @param uuid the player's uuid
     * @return whether the player is bedrock
     */
    public static Boolean isBedrock(UUID uuid) {

        if (isFloodgate() && FloodgateApi.getInstance().isFloodgatePlayer(uuid)) return true;
        if (isGeyser() && GeyserApi.api().isBedrockPlayer(uuid)) return true;
        if (proxyUsername(uuid) != null) return true;
        return isEnabled() ? false : null;
    }

    /**
     * Layer for sending Forms to bedrock players.
     * @param uuid player's uuid
     * @param form the form
     */
    public static void sendForm(UUID uuid, Form form) {
        Boolean bedrock = isBedrock(uuid);

        if (bedrock == null || !bedrock) return;

        if (isFloodgate()) {
            FloodgateApi.getInstance().sendForm(uuid, form);
        } else if (isGeyser()) {
            GeyserApi.api().sendForm(uuid, form);
        }
    }

    /** Returns the original Bedrock gamertag from the trusted server API. */
    @Nullable
    public static String bedrockUsername(UUID uuid) {
        if (isFloodgate()) {
            var player = FloodgateApi.getInstance().getPlayer(uuid);
            if (player != null) return player.getUsername();
        }
        if (isGeyser()) {
            var connection = GeyserApi.api().connectionByUuid(uuid);
            if (connection != null) return connection.bedrockUsername();
        }
        return proxyUsername(uuid);
    }

    private static String proxyUsername(UUID uuid) {
        var player = SvgCore.getPlayerManager().getPlayer(uuid);
        if (player == null || !player.isOnline()) return null;
        return ProxyBedrockIdentity.username(uuid, player.getName(),
                SvgCore.getConfig().TRUST_PROXY_BEDROCK.get(),
                SvgCore.getPlatform().isProxyForwardingEnabled(),
                SvgCore.getConfig().PROXY_BEDROCK_PREFIX.get());
    }

    //FORMS

    /**
     * Get The FormHandler
     * @return {@link FormHandler}
     */
    @Nullable
    public static FormHandler getFormHandler() {
        return formHandler;
    }

    /**
     * Create the {@link FormHandler} if it does not exist
     */
    public static void createFormHandler() {
        if (formHandler == null) {
            formHandler = new FormHandler(SvgCore.getGroupManager());
        }
    }
}
