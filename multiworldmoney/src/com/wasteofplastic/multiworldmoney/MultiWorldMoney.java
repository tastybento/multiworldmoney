/*
 * Copyright 2013-14 Ben Gibbs.
 *
 *     This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

 ****************************************************************************
 * This software is a plugin for the Bukkit Minecraft Server				*
 * It keeps balances separate  between worlds							  	*
 * Requires Vault and some kind of Economy															*
 ****************************************************************************

 */

package com.wasteofplastic.multiworldmoney;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.onarandombox.MultiverseCore.MultiverseCore;

public class MultiWorldMoney extends JavaPlugin implements Listener {
    public static Economy econ = null;
    private MultiverseCore core = null;
    boolean logDebug = false; // For debugging purposes
    File configFile;
    File playerFile;
    FileConfiguration config;
    private static HashMap<String,String> worldgroups = new HashMap<String,String>();
    private HashMap<String,UUID> onlinePlayers = new HashMap<String,UUID>();

    @Override
    public void onDisable() {
	// Save all our yamls
	//saveYamls();

	// Go through all online players and save them
	for (Player p: getServer().getOnlinePlayers()) {
	    mwmSaveOfflineWorld(p);
	}
    }

    @Override
    public void onEnable() {
	saveDefaultConfig();
	config = getConfig();
	configFile = new File(getDataFolder(), "config.yml");
	playerFile = new File(getDataFolder() + "/userdata", "temp"); // not a real file
	loadYamls();

	// Get debug setting if it exists
	logDebug = config.getBoolean("debug", false);
	if (logDebug) {
	    getLogger().info("MWM debugging is on. debug: true in config.yml");
	}
	// Hook into the Vault economy system
	setupEconomy();

	// Hook into Multiverse (if it exists)
	setupMVCore();

	// Register events
	PluginManager pm = getServer().getPluginManager();		
	pm.registerEvents(this, this); 
	// Send stats
	try {
	    MetricsLite metrics = new MetricsLite(this);
	    metrics.start();
	} catch (IOException e) {
	    // Failed to submit the stats :-(
	    getLogger().warning("Failed to submit the stats for MWM");
	}
    }

    private void logIt(String log) {
	if (!logDebug) return;
	getLogger().info(log);
    }

    private boolean setupEconomy() {
	if (getServer().getPluginManager().getPlugin("Vault") == null) {
	    return false;
	}
	RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
	if (economyProvider != null) {
	    econ = economyProvider.getProvider();
	}

	return (econ != null);    }

    private boolean setupMVCore() {
	// Multiverse plugin
	MultiverseCore mvCore;
	mvCore = (MultiverseCore) this.getServer().getPluginManager().getPlugin("Multiverse-Core");
	// Test if the Core was found
	if (mvCore == null) {
	    getLogger().warning("Multiverse-Core not found.");
	    return false;
	} else {
	    logIt("Multiverse-Core found.");
	    this.core = mvCore;
	    return true;
	}
    }

    // When a player logs in, we need to update their balance with any money that is in any world that is in the same group
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLogin(PlayerJoinEvent event) {
	// Get player object who logged in
	final Player player = event.getPlayer();
	// Get the world they are in
	final String playerWorld = player.getWorld().getName();
	// Get their name
	final String playerName = player.getName();
	// Add them to online player list
	onlinePlayers.put(playerName.toLowerCase(), player.getUniqueId());
	// Save any info
	// Create a new player file if one does not exist
	FileConfiguration playerInfo = new YamlConfiguration();
	playerFile = new File(getDataFolder() + "/userdata", playerName.toLowerCase() + ".yml");
	if (playerFile.exists()) {
	    // Get the YAML file for this player
	    try {
		playerInfo.load(playerFile);
	    } catch (Exception e) {	    		
	    }
	}
	playerInfo.set("playerinfo.uuid", onlinePlayers.get(playerName.toLowerCase()).toString());
	try {
	    playerInfo.save(playerFile);
	} catch (IOException e) {
	    e.printStackTrace();
	}




	// Get their balance in this world
	final Double balance = mwmBalance(playerName,playerWorld);
	// Get their economy balance - this ALWAYS takes precedence over MWM database because
	// the player may have received cash offline
	final Double playersBalance = econ.getBalance(player);
	if (balance.equals(playersBalance)) {
	    // Nothing to do
	    return;
	} else if (playersBalance > balance) {
	    // Just update MWM
	    mwmSet(playerName, playersBalance, playerWorld);
	    return;
	} else if (playersBalance < balance) {
	    // Give the player the cash from the other worlds
	    EconomyResponse r = econ.depositPlayer(player, (balance - playersBalance));
	    if (!(r.transactionSuccess())) {
		getLogger().severe("Balance for " + playerName + " could not be adjusted upon login");
		getLogger().severe("Debug info follows:");
		getLogger().severe("Economy balance upon login = " + playersBalance);
		getLogger().severe("MWM balance for world '" + playerWorld + "' is = " + balance);
		getLogger().severe("Error is :" + r.errorMessage);
	    }
	}
	//logIt(playerName + " now has " + econ.getBalance(playerName));
    }

    // When a player logs out, we need to store the world where they last were
    @EventHandler(ignoreCancelled = true)
    public void onLogout(PlayerQuitEvent event) {
	Player player = event.getPlayer();
	String playerName = player.getName();
	mwmSaveOfflineWorld(player);
	// Remove them from online player list
	onlinePlayers.remove(playerName.toLowerCase());
    }


    // This is the main event handler of this plugin. It adjusts balances when a player changes world
    @EventHandler(ignoreCancelled = true)
    public void onWorldLoad(PlayerChangedWorldEvent event) {
	this.logIt("Changed world!");
	// Find out who is moving world
	Player player = event.getPlayer();
	String playerName = player.getName();
	String oldWorld = event.getFrom().getName();
	String newWorld = player.getWorld().getName();
	// Retrieve the current balance of this player in oldWorld
	final Double oldBalance = econ.getBalance(player);
	// Store the old balance in the old world
	mwmSet(playerName,oldBalance,oldWorld);

	Double newBalance = 0D;
	//mwmSimpleBalance(playerName,newWorld);
	// Establish the new balance
	// Get the balance in the new world, or if the new world is in a group, then grab all balances from that group
	// Check if the new world is in a group
	if (worldgroups.containsKey(newWorld)) {
	    logIt("Player " + playerName + " changed world and new world is in a group");
	    // Any balances in group worlds MUST be grabbed and added together
	    // Sum up all worlds in the group, start from scratch
	    for (String w: worldgroups.keySet()) {
		if (inWorldGroup(w,newWorld)) {
		    logIt(w + " and " + newWorld + " are in the same group");
		    newBalance += mwmBalance(playerName,w);
		    logIt("New Balance = " + newBalance);
		    // Zero out the balance in the database now
		    mwmSet(playerName,0D,w);
		}
	    }			
	} else {
	    // New world is not in a group
	    logIt("Player " + playerName + " changed world and new world is NOT in a group");
	    newBalance = mwmBalance(playerName,newWorld);
	    // Zero out the balance in the database now
	    mwmSet(playerName,0D,newWorld);
	    logIt("New Balance = " + newBalance);
	}
	// Check for negative new balances
	if (newBalance < 0D && (!config.getBoolean("allowLoans", false))) {
	    logIt("Loans are no allowed, setting new balance to zero");
	    newBalance = 0D;
	}

	logIt("Now set the eco balance appropriately - all MWM database writes are done now");
	// Now apportion the balances
	if ((oldBalance == 0D) && (newBalance == 0D)) {
	    // They are both zero, so just do nothing
	    logIt("Both balances are zero");
	    newWorldMessage(player);
	    return;
	}
	if (oldBalance.equals(newBalance)) {
	    // They are the same
	    logIt("Player " + playerName + " changed world but both balances were the same");
	    newWorldMessage(player);
	    return;
	}
	// They are different, so find out the difference
	final Double difference = newBalance - oldBalance;
	if (difference > 0D) {
	    // The player has more money in the new world, so add to the current balance
	    final EconomyResponse r = econ.depositPlayer(player, difference);
	    if (r.transactionSuccess()) {
		logIt("Deposit: Player " + playerName + " changed world and now has " + econ.format(r.balance));
		newWorldMessage(player);
	    } else {
		getLogger().severe("Deposit: Player " + playerName + " changed worlds and their balance could not be changed for some reason!");
		getLogger().severe("Debug info follows:");
		getLogger().severe("Player Name:" + playerName + " Old World=" + oldWorld + " New World="+ newWorld);
		getLogger().severe("Old balance: " + oldBalance + " New balance= " + newBalance);
		getLogger().severe("Difference = " + difference);
		getLogger().severe("Economy error: " + r.errorMessage);
		player.sendMessage(ChatColor.RED + "An error occured when trying to set world balance! Please inform the Admin.");
	    }
	} else {
	    // Negative amount, so withdraw it
	    EconomyResponse r = econ.withdrawPlayer(player, -difference);
	    if (r.transactionSuccess()) {
		logIt("Withdraw: Player " + playerName + " changed world and now has " + econ.format(r.balance));
		newWorldMessage(player);
	    } else {
		// Check if the issue was that loans are not permitted
		if (r.errorMessage.equalsIgnoreCase("Loan was not permitted")) {
		    getLogger().severe("MWM tried to reduce the player's balance to lower than the minimum set by the economy!");
		    getLogger().severe("Trying to set player's balance to zero and fixing config.yml.");
		    // Try again
		    r = econ.withdrawPlayer(player, oldBalance);
		    if (!r.transactionSuccess()) {
			getLogger().severe("Fix did not work. Sorry, giving up. File a report on this issue or check your economy exists and is configured correctly.");
			getLogger().severe("Withdraw: Player " + playerName + " changed worlds and their balance could not be changed.");
			getLogger().severe("Player Name:" + playerName + " Old World=" + oldWorld + " New World="+ newWorld);
			getLogger().severe("Old balance: " + oldBalance + " New balance= " + newBalance);
			getLogger().severe("Difference = " + difference);
			getLogger().severe("Economy error: " + r.errorMessage);
			player.sendMessage(ChatColor.RED + "An error occured when trying to set world balance! Please inform the Admin.");
		    } else {
			getLogger().severe("Fixed config.yml and set player's balance to zero for this new world");
			newWorldMessage(player);
			// Set loan variable so this doesn't happen again
			config.set("allowLoans", false);
			saveConfig();
		    }
		} else {
		    getLogger().severe("Withdraw: Player " + playerName + " changed worlds and their balance could not be changed for some reason!");
		    getLogger().severe("Debug info follows:");
		    getLogger().severe("Player Name:" + playerName + " Old World=" + oldWorld + " New World="+ newWorld);
		    getLogger().severe("Old balance: " + oldBalance + " New balance= " + newBalance);
		    getLogger().severe("Difference = " + difference);
		    getLogger().severe("Economy error: " + r.errorMessage);
		    player.sendMessage(ChatColor.RED + "An error occured when trying to set world balance! Please inform the Admin.");
		}
	    }
	} 
	// Done!
    }

    private void newWorldMessage(Player player) {
	if (player != null) {
	    // Grab the message from the config file
	    String newWorldMessage = config.getString("newworldmessage");
	    if (newWorldMessage == null) {
		// Do not show any message
	    } else try {
		player.sendMessage(String.format(ChatColor.GOLD + newWorldMessage, econ.format(econ.getBalance(player))));
	    } catch (Exception e) {
		getLogger().severe("New world message in config.yml is malformed. Use text and one %s for the balance only.\nExample: Your balance in this world is %s");
		player.sendMessage(String.format(ChatColor.GOLD + "Your balance in this world is %s", econ.format(econ.getBalance(player))));
	    }
	}

    }

    /*
     * in here, each of the FileConfigurations loaded the contents of yamls
     *  found at the /plugins/<pluginName>/*yml.
     * needed at onEnable() after using firstRun();
     * can be called anywhere if you need to reload the yamls.
     */
    public void loadYamls() {
	try {
	    config.load(configFile); //loads the contents of the File to its FileConfiguration
	    //groups.load(groupsFile);
	    loadGroups();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    public void loadGroups() {
	YamlConfiguration groups = loadYamlFile("groups.yml");
	if(groups == null) {
	    groups = new YamlConfiguration();
	    logIt("No groups.yml found. Creating example file...");
	    ArrayList<String> exampleGroup = new ArrayList<String>();
	    exampleGroup.add("world");
	    exampleGroup.add("world_nether");
	    exampleGroup.add("world_the_end");
	    groups.set("exampleGroup", exampleGroup);
	    saveYamlFile(groups, "groups.yml");
	}
	parseGroups(groups);

    }

    public static void parseGroups(Configuration config) {
	worldgroups.clear();
	Set<String> keys = config.getKeys(false);
	for(String group : keys) {
	    List<String> worlds = config.getStringList(group);
	    for(String world : worlds) {
		worldgroups.put(world, group);
	    }
	}
    }

    public static YamlConfiguration loadYamlFile(String file) {
	File dataFolder = Bukkit.getServer().getPluginManager().getPlugin("MultiWorldMoney").getDataFolder();
	File yamlFile = new File(dataFolder, file);

	YamlConfiguration config = null;
	if(yamlFile.exists()) {
	    try {
		config = new YamlConfiguration();
		config.load(yamlFile);
	    } catch(Exception e) {
		e.printStackTrace();
	    }
	}
	return config;
    }
    /**
     * @param yamlFile
     * @param fileLocation
     */
    public static void saveYamlFile(YamlConfiguration yamlFile, String fileLocation) {
	File dataFolder = Bukkit.getServer().getPluginManager().getPlugin("MultiWorldMoney").getDataFolder();
	File file = new File(dataFolder, fileLocation);

	try {
	    yamlFile.save(file);
	} catch(Exception e) {
	    e.printStackTrace();
	}
    }

    /*
     * save all FileConfigurations to its corresponding File
     * optional at onDisable()
     * can be called anywhere if you have *.set(path,value) on your methods
     */
    /*
     * public void saveYamls() {
     */
    //try {
    // Config and groups are not changed yet, but it doesn't matter to save them
    //config.save(configFile); //saves the FileConfiguration to its File
    //groups.save(groupsFile);
    //players.save(playerFile);
    //} catch (IOException e) {
    //e.printStackTrace();
    //}
    //}*/

    /**
     * Check to see if both these worlds are in groups.yml, if not, they are not in the same group
     * @param world1
     * @param world2
     * @return
     */
    private boolean inWorldGroup(String world1, String world2) {
	// Check to see if both these worlds are in groups.yml, if not, they are not in the same group
	if (worldgroups.containsKey(world1) && worldgroups.containsKey(world2)) {
	    if (worldgroups.get(world1).equals(worldgroups.get(world2))) {
		return true;
	    }
	}
	return false;
    }

    /**
     * @param playerName
     * @param amount
     * @param world
     */
    private void mwmDeposit(String playerName, Double amount, String world) {
	FileConfiguration playersBalance = new YamlConfiguration();
	Double oldBalance = 0D;
	// Create a new player file if one does not exist
	playerFile = new File(getDataFolder() + "/userdata", playerName + ".yml");
	if (playerFile.exists()) {
	    // Get the YAML file for this player and any deposit that may exist
	    try {
		playersBalance.load(playerFile);
		oldBalance = roundDown(playersBalance.getDouble(world.toLowerCase() + ".money"),2);
	    } catch (Exception e) {
		// If that world has no balance we use 0.0
	    }
	}
	// logIt("Deposited " + econ.format(amount) + "\n to " + econ.format(oldBalance) + "in " + playerName + "'s account in world " + world);
	// Deposit
	playersBalance.set(world.toLowerCase()+ ".money", roundDown((amount + oldBalance),2));
	try {
	    playersBalance.save(playerFile);
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    /**
     * @param playerName
     * @param amount
     * @param world
     */
    private void mwmWithdraw(String playerName, Double amount, String world) {
	FileConfiguration playersBalance = new YamlConfiguration();
	Double oldBalance = 0D;
	// Create a new player file if one does not exist
	playerFile = new File(getDataFolder() + "/userdata", playerName + ".yml");
	if (playerFile.exists()) {
	    // Get the YAML file for this player and any deposit that may exist
	    try {
		playersBalance.load(playerFile);
		oldBalance = roundDown(playersBalance.getDouble(world.toLowerCase() + ".money"),2);
	    } catch (Exception e) {
		// Return 0.0 if there is no record of that world
	    }
	} 
	// logIt("Withdrew " + econ.format(amount) + "\n from " + econ.format(oldBalance) + " in " + playerName + "'s account in world " + world);
	// Withdraw
	playersBalance.set(world.toLowerCase() + ".money", roundDown((oldBalance - amount),2));
	try {
	    playersBalance.save(playerFile);
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    /**
     * Sets an amount for player in a world
     * @param playerName
     * @param amount
     * @param world
     */
    private void mwmSet(String playerName, Double amount, String world) {
	FileConfiguration playersBalance = new YamlConfiguration();
	// Create a new player file if one does not exist
	playerFile = new File(getDataFolder() + "/userdata", playerName + ".yml");
	//logIt("Set balance to " + econ.format(amount) + " for " + playerName + " in world " + world);
	// Set
	if (playerFile.exists()) {
	    // Get the YAML file for this player
	    try {
		playersBalance.load(playerFile);
	    } catch (Exception e) {
	    }
	} 
	playersBalance.set(world.toLowerCase() + ".money", roundDown(amount,2));
	try {
	    playersBalance.save(playerFile);
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    /**
     * Provides the balance of the player in a world
     * If the world is in a group, then it pulls all the balances of associated worlds into
     * that world and provides it
     * @param playerName
     * @param world
     * @return balance
     */
    private double mwmBalance(String playerName, String world) {
	// The database is file based so we need a file configuration object
	FileConfiguration playersBalances = new YamlConfiguration();
	Double balance = 0D;
	// Create a new player file if one does not exist
	playerFile = new File(getDataFolder() + "/userdata", playerName + ".yml");
	if (playerFile.exists()) {
	    // Get the YAML file for this player and any balance that may exist
	    try {
		playersBalances.load(playerFile);
		balance = roundDown(playersBalances.getDouble(world.toLowerCase() + ".money"),2);
		// Now get any other worlds in this group, but exclude the world the 
		logIt("Balance for " + playerName + " in " + world);
	    } catch (Exception e) {
		// A balance for that world does not exist so we just return zero
	    }
	}
	logIt("Balance is " + econ.format(balance) + " in world " + world);
	return balance;
    }

    /**
     * @param playerName
     * @param playerWorld
     */
    private void mwmSaveOfflineWorld(Player player) {
	String playerWorld = player.getWorld().getName();
	FileConfiguration playerInfo = new YamlConfiguration();
	// Create a new player file if one does not exist
	playerFile = new File(getDataFolder() + "/userdata", player.getName().toLowerCase() + ".yml");
	if (playerFile.exists()) {
	    // Get the YAML file for this player
	    try {
		playerInfo.load(playerFile);
	    } catch (Exception e) {	    		
	    }
	}
	// Save the logout info
	playerInfo.set("offline_world.name", playerWorld.toLowerCase());
	playerInfo.set("playerinfo.name", player.getName());
	playerInfo.set("playerinfo.uuid", player.getUniqueId().toString());
	try {
	    playerInfo.save(playerFile);
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    /**
     * Returns the name of the offline world of a player. If the player is unknown, returns null
     * @param playerName
     * @return
     */
    private String mwmReadOfflineWorld(String playerName) {
	FileConfiguration playersBalance = new YamlConfiguration();
	// Responds with the world that the player logged out in, if any otherwise null
	playerFile = new File(getDataFolder() + "/userdata", playerName.toLowerCase() + ".yml");
	if (playerFile.exists()) {
	    // Get the YAML file for this player
	    try {
		playersBalance.load(playerFile);
		return playersBalance.getString("offline_world.name").toLowerCase();
	    } catch (Exception e) {
	    }
	}
	// The player does not have a file, and therefore does not have an offline world
	return null;
    }

    /**
     * Checks if a player is online or known to MWM
     * @param playerName
     * @return
     */
    private boolean playerExists(String playerName) {
	if (onlinePlayers.containsKey(playerName.toLowerCase())) {
	    return true;
	}
	// Responds with the world that the player logged out in, if any otherwise null
	playerFile = new File(getDataFolder() + "/userdata", playerName.toLowerCase() + ".yml");
	if (playerFile.exists()) {
	    return true;
	}
	// The player does not have a file
	return false;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
	FileConfiguration players = new YamlConfiguration();
	// MWM command
	if(cmd.getName().equalsIgnoreCase("mwm")) {
	    //  Reload command
	    if (args.length == 0) {
		// No argument, tell the user what commands are available
		sender.sendMessage(String.format(ChatColor.GOLD + "MultiWorldMoney commands:\n/balance - shows balance across all worlds"));

		// Check if the player has permission
		if (sender.hasPermission("mwm.playerbalance")) {
		    sender.sendMessage(String.format(ChatColor.GOLD + "/balance <name> - Shows balance of player with name <name>"));
		}
		if (sender.hasPermission("mwm.payplayer")) {
		    sender.sendMessage(String.format(ChatColor.GOLD + "/pay <player> <amount> - Pays a player in this world an amount from your balance in this world"));
		}
		if (sender.hasPermission("mwm.reload")) {
		    sender.sendMessage(String.format(ChatColor.GOLD + "/mwm reload - Reloads the groups.yml and config.yml files"));
		}
		if (sender.hasPermission("mwm.pay")) {
		    sender.sendMessage(String.format(ChatColor.GOLD + "/mwm pay <player> <amount> <world> - Pays a player an amount from your balance to a specific world"));
		}
		if (sender.hasPermission("mwm.give")) {
		    sender.sendMessage(String.format(ChatColor.GOLD + "/mwm give <player> <amount> <world> - Gives a player an amount in a specific world"));
		}
		if (sender.hasPermission("mwm.set")) {
		    sender.sendMessage(String.format(ChatColor.GOLD + "/mwm set <player> <amount> <world> - Sets a player's balance to amount in a specific world"));
		}
		if (sender.hasPermission("mwm.take")) {
		    sender.sendMessage(String.format(ChatColor.GOLD + "/mwm take <player> <amount> <world> - Takes an amount from a player in a specific world."));
		}    			
		return true;
	    } else {
		// Check the first argument
		if (args[0].equalsIgnoreCase("reload")) {
		    // Check permission
		    if (!sender.hasPermission("mwm.reload")) {
			sender.sendMessage(String.format(ChatColor.RED + "You do not have permission to use that command."));
		    } else {
			// Reload config and groups files
			loadYamls();
			sender.sendMessage(String.format(ChatColor.GOLD + "MultiWorldMoney reloaded"));
		    }
		    return true;
		} else if (args[0].equalsIgnoreCase("set")) {
		    // Set command
		    // Check permission
		    if (!sender.hasPermission("mwm.set")) {
			sender.sendMessage(String.format(ChatColor.RED + "You do not have permission to use that command."));
			return true;
		    }
		    // Check that the right number of arguments are provided
		    if (args.length != 4) {
			sender.sendMessage(String.format(ChatColor.GOLD + "/mwm set <player> <amount> <world> - Sets a player's balance in world"));
			return true;
		    } else {
			// Check the other arguments one by one
			// Check if the player exists and can receive money
			UUID playerUUID = getUUID(args[1].toLowerCase());
			if (playerUUID == null) {
			    sender.sendMessage(String.format(ChatColor.GOLD + args[1] + " is not a recognised player"));
			    return true;
			}
			// Try to parse the amount
			double am = 0D;
			try {
			    am = roundDown(Double.parseDouble(args[2]),2);
			} catch (Exception e) {
			    sender.sendMessage(String.format(ChatColor.GOLD + "/mwm set <player> <amount> <world> - Sets a player's balance in world"));
			    return true;
			}
			String targetWorld = args[3].toLowerCase();
			// We know this player, so check if the amount is a value
			// Check the world is valid
			if (Bukkit.getWorld(targetWorld) == null) {
			    sender.sendMessage(String.format(ChatColor.GOLD + targetWorld + " is an unknown world."));
			    return true;    			        		   			        			
			} else {
			    // Find out if the player is offline or online
			    if (onlinePlayers.containsKey(args[1].toLowerCase())) {
				logIt("Target is online");
				Player op = getServer().getPlayer(onlinePlayers.get(args[1].toLowerCase()));
				String recWorld = op.getPlayer().getWorld().getName();
				// If the person is online and in the world that is the designated world or the same group, then just add to their balance
				if (recWorld.equalsIgnoreCase(targetWorld) || inWorldGroup(targetWorld,recWorld)) {
				    logIt("Target is in target world or both worlds are in the same group");
				    double diff = roundDown((am - econ.getBalance(op)),2);
				    if (diff>0D) {
					econ.depositPlayer(op, diff);
				    } else if (diff<0D) {
					EconomyResponse r = econ.withdrawPlayer(op,-diff);
					if (!r.transactionSuccess()) {
					    getLogger().severe("Failed to set " + op.getName()+ "'s balance");
					    getLogger().severe(r.errorMessage);
					    sender.sendMessage(String.format(ChatColor.RED + "Failed to set " + op.getName() + "'s balance to " + econ.format(am) + " in " + args[3] + ":" + r.errorMessage));
					    return true;
					}
				    }
				} else {
				    // They are in a totally different world. Add it to the MWM database
				    logIt("Target is not in the target world or group");
				    // Set the amount to the named player
				    mwmSet(op.getName(),am,targetWorld);
				}
			    } else {
				// Offline player - deposit the money in that world's account
				logIt("Target is offline");
				OfflinePlayer p = getServer().getOfflinePlayer(playerUUID);
				// If the player logged out in the world where the payment is being sent then credit the economy
				// Otherwise credit MWM account]
				String playerName = p.getName();
				String offlineWorld = mwmReadOfflineWorld(playerName);	
				// If the player is offline and we do not know which world they were in when they logged off then we cannot accept payment
				if (offlineWorld == null || playerUUID == null) {
				    sender.sendMessage(String.format(ChatColor.RED + "Sorry, you cannot set the balance for that offline player yet with MWM. They need to login at least one more time."));  	
				    return true;
				}
				if (offlineWorld.equalsIgnoreCase(targetWorld)) {
				    // Target's offline is the same as the pay-to world
				    double diff = roundDown((am - econ.getBalance(p)),2);
				    if (diff>0D) {
					econ.depositPlayer(p, diff);
				    } else if (diff<0D) {
					EconomyResponse r = econ.withdrawPlayer(p,-diff);
					if (!r.transactionSuccess()) {
					    getLogger().severe("Failed to set " + playerName+ "'s balance (tried to withdraw " + diff + ")");
					    getLogger().severe(r.errorMessage);
					    sender.sendMessage(String.format(ChatColor.RED + "Failed to set " + playerName + "'s balance to " + econ.format(am) + " in " + args[3] + ":" + r.errorMessage));
					    return true;
					}
				    }
				} else {
				    // Target's offline world is different to the pay to world so set it via MWM
				    mwmSet(playerName,am,targetWorld);
				}
			    }
			    sender.sendMessage(String.format(ChatColor.GOLD + "You set " + args[1] + "'s balance to " + econ.format(am) + " in " + args[3]));
			    return true;    			        		
			}
		    }
		    // End Set command
		} else if (args[0].equalsIgnoreCase("take")) {
		    // Take command
		    // Check permission
		    if (!sender.hasPermission("mwm.take")) {
			sender.sendMessage(String.format(ChatColor.RED + "You do not have permission to use that command."));
			return true;
		    }
		    // Check that the right number of arguments are provided
		    if (args.length != 4) {
			sender.sendMessage(String.format(ChatColor.GOLD + "/mwm take <player> <amount> <world> - Takes amount from a player an amount from world"));
			return true;
		    } else {
			// Check the other arguments one by one
			// Check if the player exists 
			UUID playerUUID = getUUID(args[1].toLowerCase());
			if (playerUUID == null) {
			    sender.sendMessage(String.format(ChatColor.GOLD + args[1] + " is not a recognised player"));
			    return true;
			}
			OfflinePlayer op = getServer().getOfflinePlayer(playerUUID);
			double am = 0D;
			try {
			    am = roundDown(Double.parseDouble(args[2]),2);
			} catch (Exception e) {
			    sender.sendMessage(String.format(ChatColor.GOLD + "/mwm take <player> <amount> <world> - Takes amount from a player an amount from world"));
			    return true;
			}
			String targetWorld = args[3].toLowerCase();
			if (op.hasPlayedBefore()){
			    // We know this player, so check if the amount is a value
			    if (am < 0D) {
				sender.sendMessage(String.format(ChatColor.RED + "Amounts should be positive."));
				return true;    			        		
			    } else {
				// Check the world is valid
				if (Bukkit.getWorld(targetWorld) == null) {
				    sender.sendMessage(String.format(ChatColor.RED + targetWorld + " is an unknown world."));
				    return true;    			        		   			        			
				} else {
				    // Find out if the player is offline or online
				    if (op.isOnline()) {
					// logIt("Target is online");

					String recWorld = op.getPlayer().getWorld().getName();
					// If the person is online and in the world that is the designated world or the same group, then just add to their balance
					if (recWorld.equalsIgnoreCase(targetWorld) || inWorldGroup(targetWorld,recWorld)) {
					    // logIt("Target is in target world or both worlds are in the same group");
					    econ.withdrawPlayer(op, am);
					} else {
					    // They are in a totally different world. Take it from the MWM database
					    // logIt("Target is not in the target world or group");
					    // Withdraw the amount to the named player
					    mwmWithdraw(op.getName(),am,targetWorld);
					}
				    } else {
					// Offline player - withdraw the money in that world's account
					// logIt("Target is offline");
					// If the player logged out in the world where the payment is being sent then debit the economy
					// Otherwise credit MWM account
					String offlineWorld = mwmReadOfflineWorld(op.getName());
					// If the player is offline and we do not know which world they were in when they logged off then we cannot accept payment
					if (offlineWorld == null) {
					    sender.sendMessage(String.format(ChatColor.RED + "Sorry, you cannot debit that offline player yet. They need to login at least one more time."));  	
					    return true;
					}
					if (offlineWorld.equalsIgnoreCase(targetWorld)) {
					    // logIt("Target's offline is the same as the pay to world");
					    // TODO add error checking
					    EconomyResponse r = econ.withdrawPlayer(op, am);
					    if (!r.transactionSuccess()) {
						sender.sendMessage(String.format(ChatColor.RED + "Error: " + r.errorMessage));  	
						return true;
					    }
					} else {
					    // logIt("Target's offline is different to the pay to world");
					    mwmWithdraw(op.getName(),am,targetWorld);
					}
				    }
				    sender.sendMessage(String.format(ChatColor.GOLD + "You took " + econ.format(am) + " from " + op.getName() + " in " + args[3]));
				    return true;    			        		
				}
			    } 
			}else{
			    sender.sendMessage(String.format(ChatColor.RED + op.getName() + " is not a recognised player"));
			    return true;
			}
		    }
		    // End Take command
		} else if (args[0].equalsIgnoreCase("give")) {
		    // Give command
		    // Check permission
		    if (!sender.hasPermission("mwm.give")) {
			sender.sendMessage(String.format(ChatColor.RED + "You do not have permission to use that command."));
			return true;
		    }
		    // Check that the right number of arguments are provided
		    if (args.length != 4) {
			sender.sendMessage(String.format(ChatColor.GOLD + "/mwm give <player> <amount> <world> - Gives a player an amount in a specific world"));
			return true;
		    } else {
			// Check the other arguments one by one
			// Check if the player exists 
			UUID playerUUID = getUUID(args[1].toLowerCase());
			if (playerUUID == null) {
			    sender.sendMessage(String.format(ChatColor.GOLD + args[1] + " is not a recognised player"));
			    return true;
			}
			OfflinePlayer op = getServer().getOfflinePlayer(playerUUID);
			double am = 0D;
			try {
			    am = roundDown(Double.parseDouble(args[2]),2);
			} catch (Exception e) {
			    sender.sendMessage(String.format(ChatColor.GOLD + "/mwm give <player> <amount> <world> - Gives a player an amount in a specific world"));
			    return true;
			}
			String targetWorld = args[3].toLowerCase();
			if (op.hasPlayedBefore()){
			    // We know this player, so check if the amount is a value
			    if (am < 0D) {
				sender.sendMessage(String.format(ChatColor.RED+ "Amounts should be positive."));
				return true;    			        		
			    } else {
				// Check the world is valid
				if (Bukkit.getWorld(targetWorld) == null) {
				    sender.sendMessage(String.format(ChatColor.RED + targetWorld + " is an unknown world."));
				    return true;    			        		   			        			
				} else {
				    // Find out if the player is offline or online
				    if (op.isOnline()) {
					// logIt("Target is online");

					String recWorld = op.getPlayer().getWorld().getName();
					// If the person is online and in the world that is the designated world or the same group, then just add to their balance
					logIt("Target world = " + targetWorld);
					logIt("Player's receiving world = " + recWorld);
					if (recWorld.equalsIgnoreCase(targetWorld) || inWorldGroup(targetWorld,recWorld)) {
					    logIt("Target is in target world or both worlds are in the same group");
					    EconomyResponse r = econ.depositPlayer(op, am);
					    if (!r.transactionSuccess()) {
						sender.sendMessage(String.format(ChatColor.RED + "Error: "+ r.errorMessage));  	
						return true;
					    }
					} else {
					    // They are in a totally different world. Add it to the MWM database
					    logIt("Target is not in the target world or group");
					    // Deposit the amount to the named player
					    mwmDeposit(op.getName(),am,targetWorld);
					}
				    } else {
					// Offline player - deposit the money in that world's account
					// logIt("Target is offline");
					// If the player logged out in the world where the payment is being sent then credit the economy
					// Otherwise credit MWM account
					String offlineWorld = mwmReadOfflineWorld(op.getName());
					// If the player is offline and we do not know which world they were in when they logged off then we cannot accept payment
					if (offlineWorld == null) {
					    sender.sendMessage(String.format(ChatColor.RED + "Sorry, you cannot pay that offline player yet. They need to login at least one more time."));  	
					    return true;
					}
					if (offlineWorld.equalsIgnoreCase(targetWorld)) {
					    // logIt("Target's offline is the same as the pay to world");
					    EconomyResponse r = econ.depositPlayer(op, am);
					    if (!r.transactionSuccess()) {
						sender.sendMessage(String.format(ChatColor.RED + "Error: "+ r.errorMessage));  	
						return true;
					    }
					} else {
					    // logIt("Target's offline is different to the pay to world");
					    mwmDeposit(op.getName(),am,targetWorld);
					}
				    }
				    sender.sendMessage(String.format(ChatColor.GOLD + "You gave " + econ.format(am) + " to " + op.getName() + " in " + args[3]));
				    return true;    			        		
				}
			    } 
			}else{
			    sender.sendMessage(String.format(ChatColor.RED + op.getName() + " is not a recognised player"));
			    return true;
			}
		    }
		    // End Give command
		} else if (args[0].equalsIgnoreCase("pay")) {
		    // Pay command
		    // Check that this command is being issued by a player on the server and not command line
		    if (!(sender instanceof Player)) {
			sender.sendMessage("This command can only be run by a player.");
			return true;
		    }
		    // Check permission
		    if (!sender.hasPermission("mwm.pay")) {
			sender.sendMessage(String.format(ChatColor.RED + "You do not have permission to use that command."));
			return true;
		    }
		    // Check that the right number of arguments are provided
		    if (args.length != 4) {
			sender.sendMessage(String.format(ChatColor.GOLD + "/mwm pay <player> <amount> <world> - Pays a player an amount from your balance to a specific world"));
			return true;
		    } else {
			// Check the other arguments one by one
			// Check if the player exists 
			UUID playerUUID = getUUID(args[1].toLowerCase());
			if (playerUUID == null) {
			    sender.sendMessage(String.format(ChatColor.GOLD + args[1] + " is not a recognised player"));
			    return true;
			}
			OfflinePlayer op = getServer().getOfflinePlayer(playerUUID);
			double am = 0D;
			try {
			    am = roundDown(Double.parseDouble(args[2]),2);
			} catch (Exception e) {
			    sender.sendMessage(String.format(ChatColor.RED + "/mwm pay <player> <amount> <world> - Pays a player an amount from your balance to a specific world"));
			    return true;
			}
			String targetWorld = args[3].toLowerCase();
			if (op.hasPlayedBefore()){
			    // We know this player, so check if the amount is a value
			    if (am < 0D) {
				sender.sendMessage(String.format(ChatColor.RED + "Amounts should be positive."));
				return true;    			        		
			    } else {
				// Check that the player has enough money
				if (am > econ.getBalance((OfflinePlayer)sender)) {
				    sender.sendMessage(String.format(ChatColor.RED + "Sorry, you only have " + econ.format(econ.getBalance((OfflinePlayer)sender)) + " in this world."));
				    return true; 
				}
				// Check the world is valid
				if (Bukkit.getWorld(targetWorld) == null) {
				    sender.sendMessage(String.format(ChatColor.RED + targetWorld + " is an unknown world."));
				    return true;    			        		   			        			
				} else {
				    // Find out if the player is offline or online
				    if (op.isOnline()) {
					// logIt("Target is online");

					String recWorld = op.getPlayer().getWorld().getName();
					// If the person is online and in the world that is the designated world or the same group, then just add to their balance
					if (recWorld.equalsIgnoreCase(targetWorld) || inWorldGroup(targetWorld,recWorld)) {
					    // logIt("Target is in target world or both worlds are in the same group");
					    EconomyResponse r = econ.depositPlayer(op, am);
					    if (!r.transactionSuccess()) {
						sender.sendMessage(String.format(ChatColor.RED + "Error: "+ r.errorMessage));  	
						return true;
					    } else {
						op.getPlayer().sendMessage(String.format(ChatColor.GOLD + sender.getName() + " paid you " + econ.format(am)));
					    }
					} else {
					    // They are in a totally different world. Add it to the MWM database
					    // logIt("Target is not in the target world or group");
					    // Deposit the amount to the named player
					    mwmDeposit(op.getName(),am,targetWorld);
					}
				    } else {
					// Offline player - deposit the money in that world's account
					// logIt("Target is offline");
					// If the player logged out in the world where the payment is being sent then credit the economy
					// Otherwise credit MWM account
					String offlineWorld = mwmReadOfflineWorld(op.getName());
					// If the player is offline and we do not know which world they were in when they logged off then we cannot accept payment
					if (offlineWorld == null) {
					    sender.sendMessage(String.format(ChatColor.RED + "Sorry, you cannot pay that offline player yet. They need to login at least one more time."));  	
					    return true;
					}
					if (offlineWorld.equalsIgnoreCase(targetWorld)) {
					    // logIt("Target's offline is the same as the pay to world");
					    EconomyResponse r = econ.depositPlayer(op, am);
					    if (!r.transactionSuccess()) {
						sender.sendMessage(String.format(ChatColor.RED + "Error: "+ r.errorMessage));  	
						return true;
					    }
					} else {
					    // logIt("Target's offline is different to the pay to world");
					    mwmDeposit(op.getName(),am,targetWorld);
					}
				    }
				    // Deduct the amount from the player
				    logIt("Withdrawing " + am + " from " + sender.getName());
				    EconomyResponse r = econ.withdrawPlayer((OfflinePlayer)sender, am);
				    if (!r.transactionSuccess()) {
					sender.sendMessage(String.format(ChatColor.RED + "Error: "+ r.errorMessage));  	
					return true;
				    } else {
					sender.sendMessage(String.format(ChatColor.GOLD + "You paid " + econ.format(am) + " to " + op.getName() + " in " + args[3]));
				    }
				    return true;    			        		
				}
			    } 
			}else{
			    sender.sendMessage(String.format(ChatColor.RED + op.getName() + " is not a recognised player"));
			    return true;
			}
		    }
		    // End Pay command
		} else {
		    return false;
		}
	    }
	}
	// Pay command
	if(cmd.getName().equalsIgnoreCase("pay")){ // If the player typed /pay then do the following...
	    logIt("MWM pay command");
	    // Check if this command is being issued by a player on the server and not command line
	    if (!(sender instanceof Player)) {
		// Run on the console, so must include a player's name
		sender.sendMessage("Pay is not available on console. Use mwm pay");
		return true;
	    }
	    // Check permissions
	    if (!sender.hasPermission("mwm.payplayer")) {
		sender.sendMessage(String.format(ChatColor.RED + "You do not have permission to use that command."));
		return true;
	    }
	    // Check that the right number of arguments are provided
	    if (args.length != 2) {
		// Just the pay command
		sender.sendMessage(String.format(ChatColor.GOLD + "/pay <player> <amount>"));
		return true;
	    } else {
		// Check if the player exists
		if (!playerExists(args[0])) {
		    sender.sendMessage(String.format(ChatColor.RED + "Unknown player."));
		    return true;
		}
		if (sender.getName().equalsIgnoreCase(args[0])) {
		    sender.sendMessage(String.format(ChatColor.RED + "You cannot pay yourself!"));
		    return true;
		}
		Double amount;
		try {
		    amount = Double.valueOf(args[1]);
		} catch (Exception e) {
		    sender.sendMessage(String.format(ChatColor.GOLD + "/pay <player> <amount>"));
		    return true;
		}
		if (amount <= 0D) {
		    sender.sendMessage(String.format(ChatColor.RED + "/pay <player> <positive amount>"));
		    return true; 
		}
		// Check the player has enough
		if (!econ.has((OfflinePlayer)sender,amount)) {
		    sender.sendMessage(String.format(ChatColor.RED + "You do not have enough cash to pay."));
		    sender.sendMessage(String.format(ChatColor.RED + "Your balance in this world is " + econ.format(econ.getBalance((OfflinePlayer)sender))));
		    return true; 
		}
		// Withdraw the amount
		logIt("Withdrawing " + amount + " from " + sender.getName());
		EconomyResponse r = econ.withdrawPlayer((OfflinePlayer)sender, amount);
		if (!r.transactionSuccess()) {
		    logIt("Error: could not withdraw " + amount + " from " + sender.getName());
		    logIt(r.errorMessage);
		    sender.sendMessage(ChatColor.RED + "Could could not pay for some reason.");
		    return true;
		}
		// Check if player is online
		if (onlinePlayers.containsKey(args[0].toLowerCase())) {
		    logIt("Recipient is online");
		    econ.depositPlayer(getServer().getPlayer(onlinePlayers.get(args[0].toLowerCase())), amount);
		} else {
		    logIt("Recipient is offline");
		    // Offline
		    // Just deposit the money in MWM account
		    OfflinePlayer p = getServer().getOfflinePlayer(getUUID(args[0]));
		    // Check if player logged out in this world
		    String logOutWorld = mwmReadOfflineWorld(args[0].toLowerCase());
		    if (logOutWorld != null) {
			if (logOutWorld.equalsIgnoreCase(((Player)sender).getWorld().getName())) {
			    econ.depositPlayer(p, amount);
			    sender.sendMessage(String.format(ChatColor.GOLD + "You paid " + args[0] +" " + econ.format(amount)));
			    return true;
			}
		    }
		    // Just dump it in MWM
		    mwmDeposit(args[0].toLowerCase(),amount,((Player)sender).getWorld().getName());    
		}
		sender.sendMessage(String.format(ChatColor.GOLD + "You paid " + args[0] +" " + econ.format(amount)));
		return true;
	    }
	}







	// Balance command
	if(cmd.getName().equalsIgnoreCase("balance")){ // If the player typed /balance then do the following...
	    // This flag determines if the balance report is for an offline or online player
	    Boolean offline = false;
	    // Check if this command is being issued by a player on the server and not command line
	    if (!(sender instanceof Player)) {
		// Run on the console, so must include a player's name
		// Check number of args
		if (args.length != 1) {
		    sender.sendMessage("Syntax: balance <player name>");
		    return true;
		}
	    }
	    // Check permissions
	    if (!sender.hasPermission("mwm.balance")) {
		sender.sendMessage(String.format(ChatColor.RED + "You do not have permission to use that command."));
		return true;
	    }
	    // Check that the right number of arguments are provided
	    OfflinePlayer requestedPlayer;
	    if (args.length == 0) {
		// Just the balance command
		requestedPlayer = (OfflinePlayer)sender;
	    } else {
		// Check admin permissions
		if (!sender.hasPermission("mwm.playerbalance")) {
		    sender.sendMessage(String.format(ChatColor.RED + "You do not have permission to use that command."));
		    return true;
		}
		// Check if the player exists 
		UUID playerUUID = getUUID(args[0].toLowerCase());
		if (playerUUID == null) {
		    sender.sendMessage(String.format(ChatColor.GOLD + args[0] + " is not a recognised player"));
		    return true;
		}
		OfflinePlayer op = getServer().getOfflinePlayer(playerUUID);
		if (op.hasPlayedBefore()){
		    requestedPlayer = op;
		    if (!op.isOnline()) {
			offline = true;
		    }
		} else {
		    sender.sendMessage(String.format(ChatColor.RED + "Unknown player."));
		    return true;
		}
		sender.sendMessage(String.format(ChatColor.GOLD + requestedPlayer.getName() +"'s balance:"));
	    }
	    // Find out where the player is
	    String playerWorld = "";
	    String suffix = "";
	    if (offline) {
		logIt("Player is offline");
		// Player is offline. Function returns null if there is no known MWM balance. In that case just return their economy balance
		playerWorld = mwmReadOfflineWorld(requestedPlayer.getName());
		if (playerWorld == null) {
		    logIt("Could not read offline world");
		    // Return just the current balance
		    sender.sendMessage(String.format(ChatColor.GOLD + econ.format(econ.getBalance(requestedPlayer))));
		    return true;
		} else {
		    playerWorld = playerWorld.toLowerCase();
		}
	    } else {
		// Player is online		
		logIt("Player is online");
		playerWorld = ((Player)requestedPlayer).getWorld().getName().toLowerCase();
	    }
	    // Look up details on that player
	    playerFile = new File(getDataFolder() + "/userdata", requestedPlayer.getName().toLowerCase() + ".yml");
	    Double networth = 0D;
	    Double worldBalance = 0D;
	    if (playerFile.exists()) {
		logIt("Player exists in database");
		// The list of worlds in the player's file may not include the world they are in
		// Start with the world they are in now and then add onto that based on what is in the MWM player file
		// Set the current balance in MWM database
		mwmSet(requestedPlayer.getName(),roundDown(econ.getBalance(requestedPlayer),2),playerWorld);

		// The player exists in MWM
		players = new YamlConfiguration();
		// Get the YAML file for this player
		try {
		    players.load(playerFile);
		} catch (Exception e) {
		    e.printStackTrace();
		}

		// Step through file and print out balances for each world and total at the end
		Set<String> worldList = players.getKeys(false);
		for (String s: worldList) {
		    // Check to see if this world actually exists on the server
		    final List<World> allWorlds = Bukkit.getWorlds();
		    boolean isAWorld = false;
		    for (World w : allWorlds) {
			if (w.getName().equalsIgnoreCase(s)) {
			    isAWorld = true;
			}
		    }
		    if (isAWorld) {
			worldBalance = players.getDouble(s.toLowerCase()+".money");
			networth += worldBalance;	
			// Note which world is the on the player is in
			if (s.equals(playerWorld)) {
			    suffix = ChatColor.GOLD + " (Current world)";
			} else {
			    suffix = "";
			}

			// Display balance in each world
			// The line below can be used to grab all world names
			// Collection<MultiverseWorld> wmList = core.getMVWorldManager().getMVWorlds();
			// Grab the Multiverse world name if it is available
			if (core != null) {
			    try {
				String newName = core.getMVWorldManager().getMVWorld(s).getAlias();
				s = newName;
			    } catch (Exception e)
			    {
				// do nothing if it does not work
				//logIt("Warning: Could not get name of world from Multiverse-Core for " + s);
			    }
			} 
			// Show balances
			if (worldBalance > 0D) {
			    sender.sendMessage(String.format(s + " " + ChatColor.GREEN + econ.format(worldBalance)) + suffix);
			} else if (worldBalance < 0D || (worldBalance == 0D && suffix != "")) {
			    sender.sendMessage(String.format(s + " " + ChatColor.RED + econ.format(worldBalance)) + suffix);
			} 
		    }
		}
		sender.sendMessage(String.format(ChatColor.GOLD + "Total balance across all worlds is " + econ.format(networth)));
	    } else {
		//logIt("Player does not exists in MWM");
		// The player has no MWM data so just show the current world's balance
		worldBalance = roundDown(econ.getBalance(requestedPlayer),2);
		// Find the MV alias of the world if it is available
		if (core != null) {
		    try {
			String newName = core.getMVWorldManager().getMVWorld(playerWorld).getAlias();
			playerWorld = newName;
		    } catch (Exception e)
		    {
			// do nothing if it does not work
		    }
		}

		if (worldBalance > 0D) {
		    sender.sendMessage(String.format(playerWorld + " " + ChatColor.GREEN + econ.format(worldBalance) + ChatColor.GOLD + " (Current world)"));
		} else if (worldBalance <= 0D) {
		    sender.sendMessage(String.format(playerWorld + " " + ChatColor.RED + econ.format(worldBalance)) + ChatColor.GOLD + " (Current world)");
		}
		sender.sendMessage(String.format(ChatColor.GOLD + "Total balance across all worlds is " + econ.format(worldBalance)));    			
	    }
	    return true;
	} //If this has happened the function will return true. 
	// If this hasn't happened the a value of false will be returned.
	return false; 
    }

    private UUID getUUID(String playerName) {
	if (!playerExists(playerName)) {
	    return null;
	}
	FileConfiguration playersBalance = new YamlConfiguration();
	playerFile = new File(getDataFolder() + "/userdata", playerName + ".yml");
	if (playerFile.exists()) {
	    // Get the YAML file for this player and any deposit that may exist
	    try {
		playersBalance.load(playerFile);
		String uuid = playersBalance.getString("playerinfo.uuid");
		return UUID.fromString(uuid);
	    } catch (Exception e) {
		return null;
	    }
	}
	return null;
    }

    /**
     * Rounds a double down to a set number of places
     * @param value
     * @param places
     * @return
     */
    private double roundDown(double value, int places) {
	BigDecimal bd = new BigDecimal(value);
	bd = bd.setScale(places, RoundingMode.HALF_DOWN);
	return bd.doubleValue();
    }
}
