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
            player.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + Lang.noPermission);
            return true; 
        }
        if (args.length == 2) {
            // correctly formed pay command /pay name amount
            // Check name
            UUID targetUUID = plugin.getPlayers().getUUID(args[0]);

            if (targetUUID != null) {
                if (targetUUID.equals(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + Lang.youCannotPayYourself);
                    return true;
                }
                double amount = 0;
                // Check that the amount is a number
                try {
                    amount = Double.valueOf(args[1]); // May throw NumberFormatException
                } catch (Exception ex) {
                    // Failure on the number
                    player.sendMessage(Lang.payHelp);
                    player.sendMessage("/pay " + Lang.playerHelp + " " + Lang.amountHelp);
                    return true;
                }
                if (amount < 0D) {
                    player.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + Lang.amountPositive);
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
                                sender.sendMessage(ChatColor.GREEN + ((Lang.sendTo
                                        .replace("[name]", target.getName()))
                                        .replace("[amount]", VaultHelper.econ.format(amount)))
                                        .replace("[world]", player.getWorld().getName()));
                                target.sendMessage(ChatColor.GREEN + (Lang.receiveFrom
                                        .replace("[name]", sender.getName())
                                        .replace("[amount]", VaultHelper.econ.format(amount)))
                                        .replace("[world]", player.getWorld().getName()));
                                // Override the payment
                                return true;
                            } else {
                                // Cannot pay - let pay handle the error
                                sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + Lang.insufficientFunds);
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
                    sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + Lang.noPlayer);
                    return true;
                }
            } else {
                sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + Lang.noPlayer);
                return true; 
            }
        }
        player.sendMessage(Lang.payHelp);
        player.sendMessage("/pay " + Lang.playerHelp + " " + Lang.amountHelp);
        return true;
    }


    private void pay(Player target, Player player, double amount) {
        EconomyResponse erw = VaultHelper.econ.withdrawPlayer(player, amount);
        if (erw.transactionSuccess()) {
            VaultHelper.econ.depositPlayer(target, amount);
            player.sendMessage(ChatColor.GREEN + ((Lang.sendTo
                    .replace("[name]", target.getName()))
                    .replace("[amount]", VaultHelper.econ.format(amount)))
                    .replace("[world]", player.getWorld().getName()));
            target.sendMessage(ChatColor.GREEN + (Lang.receiveFrom
                    .replace("[name]", player.getName())
                    .replace("[amount]", VaultHelper.econ.format(amount)))
                    .replace("[world]", player.getWorld().getName()));
        } else {
            // Cannot pay - let pay handle the error
            player.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + Lang.insufficientFunds);
        }
    }
}
