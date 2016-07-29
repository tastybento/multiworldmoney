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
                sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + Lang.noPermission);
                return true;
            }
        }
        switch (args.length) {
        case 1:
            if (args[0].equalsIgnoreCase("reload")) {
                plugin.reloadConfig();
                plugin.loadGroups();
                plugin.reloadLocale();
                sender.sendMessage(ChatColor.GREEN + Lang.reloaded);
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
                sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + Lang.noPlayer);
                return true;
            }
            // Check amount
            double amount = 0D;
            try {
                amount = Double.valueOf(args[2]);
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + Lang.unknownAmount);
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
                        sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + Lang.unknownWorld);
                        return true;
                    } else {
                        // Found
                        world = mvWorld.getCBWorld();
                    }
                } else {
                    // No luck
                    sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + Lang.unknownWorld);
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
                            sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + er.errorMessage);
                            return true;
                        }
                    } else if (oldBalance < amount) {
                        EconomyResponse er = VaultHelper.econ.depositPlayer(target, amount - oldBalance);
                        if (!er.transactionSuccess()) {
                            // Cannot set
                            sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + er.errorMessage);
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
                            sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + er.errorMessage);
                            return true;
                        }	
                    }
                    plugin.getPlayers().setBalance(target, world, amount);
                }
                if (!sender.equals(target)) {
                    sender.sendMessage(ChatColor.GREEN + ((Lang.setBalanceTo.replace("[name]", target.getName())).replace("[amount]",
                            VaultHelper.econ.format(amount))).replace("[world]", plugin.getWorldName(world)));
                }
                target.sendMessage(ChatColor.GREEN + (Lang.yourBalanceSetTo.replace("[amount]",VaultHelper.econ.format(amount))).replace("[world]", plugin.getWorldName(world)));
                return true;	
            } else if (args[0].equalsIgnoreCase("take")) {
                if (amount < 0D) {
                    sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + Lang.amountPositive);
                    return true;
                }
                if (groupWorlds.contains(target.getWorld())) {
                    // Same group - let the economy handle any loan issues
                    EconomyResponse er = VaultHelper.econ.withdrawPlayer(target, amount);
                    if (!er.transactionSuccess()) {
                        // Cannot set
                        sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + er.errorMessage);
                        return true;
                    }
                } else { 
                    // Not in the same group
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
                            sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + er.errorMessage);
                            return true;
                        }	
                    }
                    plugin.getPlayers().withdraw(target, world, amount);
                }
                if (!sender.equals(target)) {
                    sender.sendMessage(ChatColor.GREEN + ((Lang.withdrew.replace("[name]", target.getName())).replace("[amount]",
                            VaultHelper.econ.format(amount))).replace("[world]", plugin.getWorldName(world)));
                }
                target.sendMessage(ChatColor.GREEN + (Lang.reduceBalance.replace("[amount]",VaultHelper.econ.format(amount))).replace("[world]", plugin.getWorldName(world)));
                return true;
            } else if (args[0].equalsIgnoreCase("give")) {
                if (amount < 0D) {
                    sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + Lang.amountPositive);
                    return true;
                }
                // Check which world the player is in
                if (groupWorlds.contains(target.getWorld())) {
                    EconomyResponse er = VaultHelper.econ.depositPlayer(target, amount);
                    if (!er.transactionSuccess()) {
                        // Cannot set
                        sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + er.errorMessage);
                        return true;
                    }
                } else {
                    plugin.getPlayers().deposit(target, world, amount);
                }
                if (!sender.equals(target)) {
                    sender.sendMessage(ChatColor.GREEN + ((Lang.deposited.replace("[name]", target.getName())).replace("[amount]",
                            VaultHelper.econ.format(amount))).replace("[world]", plugin.getWorldName(world)));
                }
                target.sendMessage(ChatColor.GREEN + (Lang.increasedBalance.replace("[amount]",VaultHelper.econ.format(amount))).replace("[world]", plugin.getWorldName(world)));
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
        sender.sendMessage("/mwm reload - " + Lang.reloadHelp);
        sender.sendMessage("/mwm set " + Lang.playerHelp + " " + Lang.balanceHelp + " " + Lang.worldHelp + " - " + Lang.setHelp);
        sender.sendMessage("/mwm take " + Lang.playerHelp + " " + Lang.amountHelp + " " + Lang.worldHelp + " - " + Lang.takeHelp);
        sender.sendMessage("/mwm give " + Lang.playerHelp + " " + Lang.balanceHelp + " " + Lang.worldHelp + " - " + Lang.giveHelp);
    }



}
