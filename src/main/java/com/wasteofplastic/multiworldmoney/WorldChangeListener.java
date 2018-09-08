package com.wasteofplastic.multiworldmoney;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import net.milkbowl.vault.economy.EconomyResponse;

class WorldChangeListener implements Listener {
    private final MultiWorldMoney plugin;


    /**
     * @param plugin plugin
     */
    public WorldChangeListener(MultiWorldMoney plugin) {
        this.plugin = plugin;
    }


    @EventHandler(ignoreCancelled = true)
    public void onWorldLoad(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World from = event.getFrom();
        if (from != null) {
            // Deduce what the new balance needs to be
            // All worlds in the same group use the same balance
            List<World> groupWorlds = plugin.getGroupWorlds(from);
            if (groupWorlds.contains(player.getWorld())) {
                plugin.getPlayers().setBalance(player, from, 0D);
                // The world they are moving to is in the same group, so keep the balance, but remove it from the
                // last world otherwise there will be a dupe
            } else {
                // The player has moved to a new group
                // Save their balance in the old world that they just left
                double oldBalance = plugin.roundDown(plugin.getVh().getEcon().getBalance(player),2);
                plugin.getPlayers().setBalance(player, from, oldBalance);
                // Now work out what their new balance needs to be
                double newBalance = 0D;
                // Rationalize all the money in the group worlds and put it into the player's world
                for (World world : plugin.getGroupWorlds(player.getWorld())) {
                    newBalance += plugin.getPlayers().getBalance(player, world);
                    // Set the balance to zero in these other worlds
                    if (!world.equals(player.getWorld())) {
                        plugin.getPlayers().setBalance(player, world, 0D);
                    }
                }
                plugin.getPlayers().setBalance(player, player.getWorld(), newBalance);
                // Now make the player's economy balance this value
                if (oldBalance > newBalance) {
                    // Withdraw some money to make the oldBalance = newBalance
                    EconomyResponse response = plugin.getVh().getEcon().withdrawPlayer(player, (oldBalance-newBalance));
                    if (!response.transactionSuccess()) {
                        if (response.errorMessage != null && response.errorMessage.equalsIgnoreCase("Loan was not permitted")) {
                            plugin.getLogger().warning("Negative balances not permitted by economy. Setting balance to zero.");
                            plugin.getVh().getEcon().withdrawPlayer(player, oldBalance);
                        }
                    }
                } else if (oldBalance < newBalance) {
                    plugin.getVh().getEcon().depositPlayer(player, (newBalance-oldBalance));
                }
            }
            // Done - show the balance if requested
            if (plugin.getSettings().isShowBalance()) {
                player.sendMessage(ChatColor.GOLD + plugin.getSettings().getNewWorldMessage().replace("[balance]"
                        , plugin.getVh().getEcon().format(plugin.getVh().getEcon().getBalance(player))));
            }
        }
    }
}
