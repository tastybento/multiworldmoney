package com.wasteofplastic.multiworldmoney;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

class LogInOutListener implements Listener {

    private final MultiWorldMoney plugin;

    /**
     * @param plugin - plugin
     */
    public LogInOutListener(MultiWorldMoney plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Add player to the cache
        plugin.getPlayers().addPlayer(player);
        // Set the balance for this player in whatever world they are in because it may have changed while
        // they were offline
        double balance = plugin.getVh().getEcon().getBalance(player);
        plugin.getPlayers().setBalance(player, player.getWorld(), balance);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        // Remove player
        plugin.getPlayers().removePlayer(event.getPlayer());
    }

}
