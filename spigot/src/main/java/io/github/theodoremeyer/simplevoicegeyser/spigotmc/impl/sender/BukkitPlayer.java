package io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.sender;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.SvgPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

public class BukkitPlayer extends SvgPlayer {

    private final Player player;

    public BukkitPlayer(Player player) {
        this.player = player;
    }

    @Override
    public UUID getUniqueId() {
        return player.getUniqueId();
    }

    @Override
    public String getName() {
        return player.getName();
    }

    @Override
    public boolean hasPermission(String permission) {
        return player.hasPermission(permission);
    }

    @Override
    public void chat(String message) {
        if (Bukkit.isPrimaryThread()) {
            player.chat(message);
        } else {
            SvgPlugin plugin = (SvgPlugin) SvgCore.getPlatform();

            Bukkit.getScheduler().runTask(plugin, () -> player.chat(message));
        }
    }

    @Override
    public boolean isOnline() {
        return player.isOnline();
    }

    @Override
    public Object getPlayer() {
        return player;
    }

    @Override public String getSkinUrl() {
        var skin = player.getPlayerProfile().getTextures().getSkin();
        if (skin == null || !skin.getHost().equalsIgnoreCase("textures.minecraft.net")
                || !skin.getPath().matches("/texture/[a-fA-F0-9]{32,64}")) return "";
        return "https://textures.minecraft.net" + skin.getPath();
    }
    @Override public boolean canSee(SvgPlayer other) {
        return other instanceof BukkitPlayer b && player.canSee(b.player);
    }
    @Override public boolean isNearby(SvgPlayer other, double range) {
        if (!(other instanceof BukkitPlayer b) || !canSee(other) || !player.getWorld().equals(b.player.getWorld())) return false;
        return player.getLocation().distanceSquared(b.player.getLocation()) <= range * range;
    }

    @Override
    public void sendMessage(String message) {
        runOnMainThread(() -> player.sendMessage(translate(message)));
    }

    private void runOnMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }

        Bukkit.getScheduler().runTask(
                SvgPlugin.getPlugin(SvgPlugin.class),
                task
        );
    }

    private String translate(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
