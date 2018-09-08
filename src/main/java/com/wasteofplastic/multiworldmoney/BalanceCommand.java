package com.wasteofplastic.multiworldmoney;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

class BalanceCommand implements CommandExecutor {

    private final MultiWorldMoney plugin;

    /**
     * @param plugin - plugin
     */
    public BalanceCommand(MultiWorldMoney plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /balance command
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + "/balance is only available in game. Use /balance <name>");
                return true;
            }
            Player player = (Player)sender;
            if (VaultHelper.checkPerm(player, "mwm.balance")) {
                player.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + Lang.noPermission);
                return true; 
            }
            // Show balance in each world
            double total = 0D;
            for (World world : plugin.getServer().getWorlds()) {
                double amount = plugin.getPlayers().getBalance(player, world);
                if (player.getWorld().equals(world)) {
                    amount = VaultHelper.econ.getBalance(player);
                }
                if (amount > 0D) {
                    total += amount;
                    player.sendMessage(plugin.getWorldName(world) + ": " + VaultHelper.econ.format(amount));
                } else if (amount < 0D) {
                    total += amount;
                    player.sendMessage(plugin.getWorldName(world) + ": " + ChatColor.RED + VaultHelper.econ.format(amount));
                }
            }
            player.sendMessage("Total : " + VaultHelper.econ.format(total));
            return true;
        } else if (args.length == 1) {
            // balance <name> command
            if (!sender.hasPermission("mwm.playerbalance")) {
                sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + Lang.noPermission);
                return true;
            }
            // Check if the player exists 
            UUID targetUUID = plugin.getPlayers().getUUID(args[0]);
            if (targetUUID == null) {
                //plugin.getLogger().info("DEBUG: player is not in list of known players");
                sender.sendMessage(ChatColor.RED + "Error: " + ChatColor.DARK_RED + Lang.noPlayer);
                return true;
            }
            // See if they are online
            Player target = plugin.getServer().getPlayer(targetUUID);
            if (target != null) {
                // Online
                // Show balance in each world
                sender.sendMessage(Lang.balanceFor.replace("[name]",target.getName()));
                double total = 0D;
                for (World world : plugin.getServer().getWorlds()) {
                    double amount = plugin.getPlayers().getBalance(target, world);
                    if (target.getWorld().equals(world)) {
                        amount = VaultHelper.econ.getBalance(target);
                    }
                    if (amount > 0D) {
                        total += amount;
                        sender.sendMessage(plugin.getWorldName(world) + ": " + VaultHelper.econ.format(amount));
                    } else if (amount < 0D) {
                        total += amount;
                        sender.sendMessage(plugin.getWorldName(world) + ": " + ChatColor.RED + VaultHelper.econ.format(amount));
                    }
                }
                sender.sendMessage(Lang.total.replace("[total]", VaultHelper.econ.format(total)));
                return true;
            }
            // Offline player
            //plugin.getLogger().info("DEBUG: offline player");
            File userFolder = new File(plugin.getDataFolder(),"players");
            if (!userFolder.exists()) {
                userFolder.mkdir();
            }
            // Load the info on this player
            File userFile = new File(userFolder, targetUUID.toString() + ".yml");
            YamlConfiguration playerConfig = new YamlConfiguration();
            if (userFile.exists()) { 
                sender.sendMessage(Lang.offlineBalance.replace("[balance]",plugin.getPlayers().getName(targetUUID)));
                double total = 0D;
                //plugin.getLogger().info("DEBUG: loading file ");
                try {
                    playerConfig.load(userFile);
                    String logOutworld = playerConfig.getString("logoffworld", "");
                    // Get the last world. Could be null if it's not recognized
                    World logoffWorld = plugin.getServer().getWorld(logOutworld);
                    if (playerConfig.contains("balances")) {
                        //plugin.getLogger().info("DEBUG: Balances section exists");
                        // Load the balances
                        for (String worldName : playerConfig.getConfigurationSection("balances").getKeys(false)) {
                            //plugin.getLogger().info("DEBUG: loading " + world);
                            World world = plugin.getServer().getWorld(worldName);
                            if (world != null) {
                                double amount = playerConfig.getDouble("balances." + worldName, 0D);
                                if (world == logoffWorld) {
                                    amount = VaultHelper.econ.getBalance(plugin.getServer().getOfflinePlayer(targetUUID));
                                }
                                if (amount > 0D) {
                                    total += amount;
                                    sender.sendMessage(plugin.getWorldName(world) + ": " + VaultHelper.econ.format(amount));
                                } else if (amount < 0D) {
                                    total += amount;
                                    sender.sendMessage(plugin.getWorldName(world) + ": " + ChatColor.RED + VaultHelper.econ.format(amount));
                                }
                            }
                        }
                    }
                } catch (InvalidConfigurationException | IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                sender.sendMessage(Lang.total.replace("[total]", VaultHelper.econ.format(total)));
                return true;
            } else {
                sender.sendMessage(ChatColor.RED + Lang.error + " " + ChatColor.DARK_RED + Lang.noPlayer);
                return true; 
            }
        }
        return false;
    }

}
