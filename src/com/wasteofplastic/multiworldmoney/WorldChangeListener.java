package com.wasteofplastic.multiworldmoney;

import java.util.List;

import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.World;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

public class WorldChangeListener implements Listener {
    private MultiWorldMoney plugin;


    /**
     * @param plugin
     */
    public WorldChangeListener(MultiWorldMoney plugin) {
	this.plugin = plugin;
    }


    @EventHandler(ignoreCancelled = true)
    public void onWorldLoad(PlayerChangedWorldEvent event) {
	//plugin.getLogger().info("DEBUG: " + event.getEventName());
	Player player = event.getPlayer();
	World from = event.getFrom();
	if (from != null) {
	    //plugin.getLogger().info("DEBUG: from is not null");
	    // Deduce what the new balance needs to be
	    // All worlds in the same group use the same balance
	    List<World> groupWorlds = plugin.getGroupWorlds(from);
	    //plugin.getLogger().info("DEBUG: from group worlds = " + groupWorlds);
	    if (groupWorlds.contains(player.getWorld())) {
		plugin.getPlayers().setBalance(player, from, 0D);
		//plugin.getLogger().info("DEBUG: same group, just shift balance");
		// The world they are moving to is in the same group, so keep the balance, but remove it from the
		// last world otherwise there will be a dupe
	    } else {
		// The player has moved to a new group
		// Save their balance in the old world that they just left
		double oldBalance = plugin.roundDown(VaultHelper.econ.getBalance(player),2);
		//plugin.getLogger().info("DEBUG: old balance = " + oldBalance);
		plugin.getPlayers().setBalance(player, from, oldBalance);
		// Now work out what their new balance needs to be
		double newBalance = 0D;
		// Rationalize all the money in the group worlds and put it into the player's world
		//plugin.getLogger().info("DEBUG: new group of worlds = " + plugin.getGroupWorlds(player.getWorld()));
		for (World world : plugin.getGroupWorlds(player.getWorld())) {
		    //plugin.getLogger().info("DEBUG: balance in " + world.getName() + " = " + plugin.getPlayers().getBalance(player, world));
		    newBalance += plugin.getPlayers().getBalance(player, world);
		    // Set the balance to zero in these other worlds
		    if (!world.equals(player.getWorld())) {
			plugin.getPlayers().setBalance(player, world, 0D);
		    }
		}
		plugin.getPlayers().setBalance(player, player.getWorld(), newBalance);
		//plugin.getLogger().info("DEBUG: new Balance = " + newBalance);
		// Now make the player's economy balance this value
		if (oldBalance > newBalance) {
		    // Withdraw some money to make the oldBalance = newBalance
		    EconomyResponse response = VaultHelper.econ.withdrawPlayer(player, (oldBalance-newBalance));
		    if (!response.transactionSuccess()) {
			if (response.errorMessage.equalsIgnoreCase("Loan was not permitted")) {
			    plugin.getLogger().warning("Negative balances not permitted by economy. Setting balance to zero.");
			    VaultHelper.econ.withdrawPlayer(player, oldBalance);
			}
		    }
		} else if (oldBalance < newBalance) {
		    VaultHelper.econ.depositPlayer(player, (newBalance-oldBalance));
		}
	    }
	    // Done - show the balance if requested
	    if (Settings.showBalance) {
		player.sendMessage(ChatColor.GOLD + Settings.newWorldMessage.replace("[balance]"
			, VaultHelper.econ.format(VaultHelper.econ.getBalance(player))));
	    }
	}
    }
}
