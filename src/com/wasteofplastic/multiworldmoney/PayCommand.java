package com.wasteofplastic.multiworldmoney;

import java.util.List;
import java.util.UUID;

import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PayCommand implements CommandExecutor {

    private MultiWorldMoney plugin;

    /**
     * @param plugin
     */
    public PayCommand(MultiWorldMoney plugin) {
	this.plugin = plugin;
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
	if (!(sender instanceof Player)) {
	    sender.sendMessage("Error: /pay is only available in game.");
	    return true;
	}
	Player player = (Player)sender;
	if (!VaultHelper.checkPerm(player, "mwm.pay")) {
	    player.sendMessage(String.format(ChatColor.RED + "You do not have permission to use that command."));
	    return true; 
	}
	if (args.length == 2) {
	    // correctly formed pay command /pay name amount
	    // Check name
	    UUID targetUUID = plugin.getPlayers().getUUID(args[0]);
	    
	    if (targetUUID != null) {
		if (targetUUID.equals(player.getUniqueId())) {
			player.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + "You cannot pay yourself.");
			return true;
		    }
		double amount = 0;
		// Check that the amount is a number
		try {
		    amount = Double.valueOf(args[1]); // May throw NumberFormatException
		} catch (Exception ex) {
		    // Failure on the number
		    player.sendMessage("Pays another player from your balance");
		    player.sendMessage("/pay <player> <amount>");
		    return true;
		}
		if (amount < 0D) {
		    player.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + "Amount must be positive.");
		    return true;
		}
		// Check if online or offline
		Player target = plugin.getServer().getPlayer(targetUUID);
		if (target != null) {
		    // online player
		    // Check worlds
		    if (!target.getWorld().equals(player.getWorld())) {
			// Not same world
			// Check if worlds are in the same group
			List<World> groupWorlds = plugin.getGroupWorlds(target.getWorld());
			//plugin.getLogger().info("DEBUG: from group worlds = " + groupWorlds);
			if (!groupWorlds.contains(player.getWorld())) {
			    // They are not in the same group
			    // Try to withdraw the amount
			    EconomyResponse er = VaultHelper.econ.withdrawPlayer(player, amount);
			    if (er.transactionSuccess()) {
				// Set the balance in the sender's world
				plugin.getPlayers().deposit(target, player.getWorld(), amount);
				player.sendMessage(ChatColor.GREEN + VaultHelper.econ.format(amount) + " has been sent to " + target.getName() + " in world " + player.getWorld().getName());
				target.sendMessage(ChatColor.GREEN + VaultHelper.econ.format(amount) + " has been received from " + player.getName() + " in world " + player.getWorld().getName());
				// Override the payment
				return true;
			    } else {
				// Cannot pay - let pay handle the error
				sender.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + "You do not have sufficient funds.");
				return true;
			    }
			} else {
			    // else allow the payment
			    pay(target, player, amount);
			    return true;
			}
		    } 
		    // Same world - allow the transfer
		    pay(target, player, amount);
		    return true;
		} else {
		    // Offline player - not supported
		    sender.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + "Player not found.");
		    return true;
		}
	    } else {
		sender.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + "Player not found.");
		return true; 
	    }
	}
	player.sendMessage("Pays another player from your balance");
	player.sendMessage("/pay <player> <amount>");
	return true;
    }


    private void pay(Player target, Player player, double amount) {
	EconomyResponse erw = VaultHelper.econ.withdrawPlayer(player, amount);
	if (erw.transactionSuccess()) {
	    VaultHelper.econ.depositPlayer(target, amount);
	    player.sendMessage(ChatColor.GREEN + VaultHelper.econ.format(amount) + " has been sent to " + target.getName() + " in world " + player.getWorld().getName());
	    target.sendMessage(ChatColor.GREEN + VaultHelper.econ.format(amount) + " has been received from " + player.getName() + " in world " + player.getWorld().getName());
	} else {
	    // Cannot pay - let pay handle the error
	    player.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + "You do not have sufficient funds.");
	}
    }
}
