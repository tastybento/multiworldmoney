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

import com.onarandombox.MultiverseCore.api.MultiverseWorld;

public class AdminCommands implements CommandExecutor {

    private MultiWorldMoney plugin;

    /**
     * @param plugin
     */
    public AdminCommands(MultiWorldMoney plugin) {
	this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
	if (sender instanceof Player) {
	    // Check permission
	    if (!VaultHelper.checkPerm((Player)sender, "mwm.admin")) {
		sender.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + "You do not have permission to do that.");
		return true;
	    }
	}
	switch (args.length) {
	case 1:
	    if (args[0].equalsIgnoreCase("reload")) {
		sender.sendMessage(ChatColor.GREEN + "Reloaded configuration.");
		plugin.loadConfig();
		return true;
	    } else {
		printHelp(sender);
		return true;
	    }
	case 4:
	    // Check player name
	    UUID targetUUID = plugin.getPlayers().getUUID(args[1]);
	    Player target= plugin.getServer().getPlayer(targetUUID);
	    if (targetUUID == null || target == null) {
		sender.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + "Player not found.");
		return true;
	    }
	    // Check amount
	    double amount = 0D;
	    try {
		amount = Double.valueOf(args[2]);
	    } catch (Exception e) {
		sender.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + "Unknown amount.");
		return true;
	    }
	    // Round down to 2 dp's
	    amount = plugin.roundDown(amount, 2);
	    // Check world
	    World world = plugin.getServer().getWorld(args[3]);
	    if (world == null) {
		// Try MV
		if (plugin.getCore() != null) {
		    MultiverseWorld mvWorld = plugin.getCore().getMVWorldManager().getMVWorld(args[3]);
		    if (mvWorld == null) {
			sender.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + "Unknown world.");
			return true;
		    } else {
			// Found
			world = mvWorld.getCBWorld();
		    }
		} else {
		    // No luck
		    sender.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + "Unknown world.");
		    return true;
		}
	    }
	    // Get group worlds
	    List<World> groupWorlds = plugin.getGroupWorlds(world);
	    if (args[0].equalsIgnoreCase("set")) {
		if (groupWorlds.contains(target.getWorld())) {
		    // Set
		    double oldBalance = plugin.roundDown(VaultHelper.econ.getBalance(target), 2);
		    if (oldBalance > amount) {
			EconomyResponse er = VaultHelper.econ.withdrawPlayer(target, oldBalance - amount);
			if (!er.transactionSuccess()) {
			    // Cannot set
			    sender.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + er.errorMessage);
			    return true;
			}
		    } else if (oldBalance < amount) {
			EconomyResponse er = VaultHelper.econ.depositPlayer(target, amount - oldBalance);
			if (!er.transactionSuccess()) {
			    // Cannot set
			    sender.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + er.errorMessage);
			    return true;
			}
		    }
		} else {
		    if (amount < 0D) {
			// It will - see if loans are allowed
			double balance = VaultHelper.econ.getBalance(target) - amount;
			// Try to withdraw an amount and see what happens
			EconomyResponse er = VaultHelper.econ.withdrawPlayer(target, balance);
			if (er.transactionSuccess()) {
			    // Put it back
			    VaultHelper.econ.depositPlayer(target, balance);
			} else {
			    sender.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + er.errorMessage);
			    return true;
			}	
		    }
		    plugin.getPlayers().setBalance(target, world, amount);
		}
		sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s balance to " 
			+ VaultHelper.econ.format(amount) + " in " + plugin.getWorldName(world));
		target.sendMessage(ChatColor.GREEN + "Your balance was set to " 
			+ VaultHelper.econ.format(amount) + " in " + plugin.getWorldName(world));
		return true;	
	    } else if (args[0].equalsIgnoreCase("take")) {
		if (amount < 0D) {
		    sender.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + "Amount must be positive.");
		    return true;
		}
		// Check to see if loans are allowed
		// Check to see if this will result in a negative balance
		if (amount > plugin.getPlayers().getBalance(target, world)) {
		    // It will - see if loans are allowed
		    double balance = VaultHelper.econ.getBalance(target) + amount - plugin.getPlayers().getBalance(target, world);
		    // Try to withdraw an amount and see what happens
		    EconomyResponse er = VaultHelper.econ.withdrawPlayer(target, balance);
		    if (er.transactionSuccess()) {
			// Put it back
			VaultHelper.econ.depositPlayer(target, balance);
		    } else {
			sender.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + er.errorMessage);
			return true;
		    }	
		}
		if (groupWorlds.contains(target.getWorld())) {
		    EconomyResponse er = VaultHelper.econ.withdrawPlayer(target, amount);
		    if (!er.transactionSuccess()) {
			// Cannot set
			sender.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + er.errorMessage);
			return true;
		    }
		} else { 
		    plugin.getPlayers().withdraw(target, world, amount);
		}
		sender.sendMessage(ChatColor.GREEN + "Withdrew " + VaultHelper.econ.format(amount) 
			+ " from " + target.getName() + " in " + plugin.getWorldName(world));
		target.sendMessage(ChatColor.GREEN + "Your balance in " + plugin.getWorldName(world)
			+ " decreased by " + VaultHelper.econ.format(amount));
		return true;
	    } else if (args[0].equalsIgnoreCase("give")) {
		if (amount < 0D) {
		    sender.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + "Amount must be positive.");
		    return true;
		}
		// Check which world the player is in
		if (groupWorlds.contains(target.getWorld())) {
		    EconomyResponse er = VaultHelper.econ.depositPlayer(target, amount);
		    if (!er.transactionSuccess()) {
			// Cannot set
			sender.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + er.errorMessage);
			return true;
		    }
		} else {
		    plugin.getPlayers().deposit(target, world, amount);
		}
		sender.sendMessage(ChatColor.GREEN + "Deposited " + VaultHelper.econ.format(amount) 
			+ " from " + target.getName() + " in " + plugin.getWorldName(world));
		target.sendMessage(ChatColor.GREEN + "Your balance in " + plugin.getWorldName(world)
			+ " increased by " + VaultHelper.econ.format(amount));
		return true;
	    }
	    return false;
	default:
	    // Print help
	    printHelp(sender);
	    return true;
	}
    }

    private void printHelp(CommandSender sender) {
	sender.sendMessage("/mwm reload - Reloads the MWM config");
	sender.sendMessage("/mwm set <player> <balance> <world> - Sets the player's balance in world");
	sender.sendMessage("/mwm take <player> <amount> <world> - Takes amount from player in world");
	sender.sendMessage("/mwm give <player> <balance> <world> - Gives amount to player in world");
    }



}
