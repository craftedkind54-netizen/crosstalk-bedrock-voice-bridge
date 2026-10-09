package io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.sender.BukkitPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class SvgListener implements Listener {
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVoiceCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        String[] parts = event.getMessage().trim().split("\\s+");
        if (parts.length != 3 || !(parts[0].equalsIgnoreCase("/voicechat") || parts[0].equalsIgnoreCase("/voicechat:voicechat"))) return;
        var manager = SvgCore.getGroupManager();
        if (manager == null) return;
        var sender = SvgCore.getPlayerManager().getPlayer(event.getPlayer().getUniqueId());
        if (sender == null) return;
        if (parts[1].equalsIgnoreCase("join") && manager.browser.hasInvite(sender, parts[2])) {
            event.setCancelled(true);
            try { manager.browser.accept(sender, parts[2]); sender.sendMessage("[CrossTalk] Voice group invite accepted."); }
            catch (IllegalArgumentException e) { sender.sendMessage("[CrossTalk] " + e.getMessage()); }
        } else if (parts[1].equalsIgnoreCase("invite")) {
            for (var target : SvgCore.getPlayerManager().getAllPlayers()) {
                String bedrockName = io.github.theodoremeyer.simplevoicegeyser.core.geyser.GeyserHook.bedrockUsername(target.getUniqueId());
                if (!target.getName().equals(parts[2]) && !parts[2].equals(bedrockName)) continue;
                if (!Boolean.TRUE.equals(io.github.theodoremeyer.simplevoicegeyser.core.geyser.GeyserHook.isBedrock(target.getUniqueId()))
                        && !Boolean.TRUE.equals(io.github.theodoremeyer.simplevoicegeyser.core.geyser.GeyserHook.isBedrock(sender.getUniqueId()))) return;
                event.setCancelled(true);
                try { sender.sendMessage("[CrossTalk] " + manager.browser.invite(sender, target)); }
                catch (IllegalArgumentException e) { sender.sendMessage("[CrossTalk] " + e.getMessage()); }
                return;
            }
        }
    }


    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        BukkitPlayer bukkitPlayer = new BukkitPlayer(player);
        SvgCore.getJoinMessageHandler().sendJoinMessage(bukkitPlayer);

        SvgCore.getPlayerManager().addPlayer(bukkitPlayer);
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        SvgPlayer player = SvgCore.getPlayerManager().getPlayer(playerUuid);

        if (player != null) {
            SvgCore.getPlayerManager().removePlayer(player);
        }
    }
}
