/*
 * Copyright 2013-14 Ben Gibbs. All rights reserved.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
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
    //File groupsFile;
    FileConfiguration config;
    //FileConfiguration players;
    //FileConfiguration groups;
    private static HashMap<String,String> worldgroups = new HashMap<String,String>();
   
    @Override
    public void onDisable() {
        // Save all our yamls
        //saveYamls();
    }
    
    @Override
	public void onEnable() {
	    configFile = new File(getDataFolder(), "config.yml");
	    playerFile = new File(getDataFolder() + "/userdata", "temp"); // not a real file
	    
	    // Hook into the Vault economy system
		setupEconomy();
		
		// Hook into Multiverse (if it exists)
		setupMVCore();
		
		// Check if this is the first time this plug in has been run
		PluginManager pm = getServer().getPluginManager();		
		pm.registerEvents(this, this);
	    try {
	        firstRun();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
        // then we just use loadYamls(); method
        config = new YamlConfiguration();
        //players = new YamlConfiguration();
        //groups = new YamlConfiguration();
        loadYamls();
                
    	// Send stats
    	try {
    	    MetricsLite metrics = new MetricsLite(this);
    	    metrics.start();
    	} catch (IOException e) {
    	    // Failed to submit the stats :-(
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
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }
    
    private boolean setupMVCore() {
        // Multiverse plugin
        MultiverseCore mvCore;
        mvCore = (MultiverseCore) this.getServer().getPluginManager().getPlugin("Multiverse-Core");
        // Test if the Core was found
        if (mvCore == null) {
        	getLogger().info("Multiverse-Core not found.");
        	return false;
        } else {
        	getLogger().info("Multiverse-Core found.");
        	this.core = mvCore;
        	return true;
        }
    }
    
    // When a player logs in, we need to update their balance with any money that is in any world that is in the same group
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLogin(PlayerJoinEvent event) {
    	// Get player object who logged in
    	Player player = event.getPlayer();
    	// Get the world they are in
    	String playerWorld = player.getWorld().getName();
    	// Get their name
    	String playerName = player.getName();
     	//logIt(playerName + " logged in to " + playerWorld + " and they have " + econ.getBalance(playerName));
    	// Go through each world and if they are in the same group, grab that world's balance and add it to the player's current balance
    	for (String world: worldgroups.keySet()) {
    		//logIt("World in world group list is = " + world);
    		if (inWorldGroup(world, playerWorld) && !world.equals(playerWorld)) {
    			//logIt("Player's world '" + playerWorld + " is in a group - checking balance in " + world);
    			// Remove any money from the world in the group
    			double oldBalance = mwmBalance(playerName, world);
    			econ.depositPlayer(playerName, oldBalance);
    			//logIt("deposited " + oldBalance + " for " + playerName + " from " + world);
    			mwmSet(playerName,0.0,world);
    		}
    	}
     	//logIt(playerName + " now has " + econ.getBalance(playerName));
    }
 
    // When a player logs out, we need to store the world where they last were
    @EventHandler(ignoreCancelled = true)
    public void onLogout(PlayerQuitEvent event) {
    	Player player = event.getPlayer();
    	String playerWorld = player.getWorld().getName();
    	String playerName = player.getName();
    	mwmSaveOfflineWorld(playerName,playerWorld);
    }

    
    // This is the main event handler of this plugin. It adjusts balances when a player changes world
	@EventHandler(ignoreCancelled = true)
	public void onWorldLoad(PlayerChangedWorldEvent event) {
		// Find out who is moving world
		Player player = event.getPlayer();
		FileConfiguration players = new YamlConfiguration();
		// Check to see if they are in a grouped world
		// If new world is in same group as old world, move the money from one world to the other
		// If player moves from a world outside a group into a group, then add up all the balances in that group, zero them out and give them to the player and set the new world balance to be the total
		// If a player moves within a group, then zero out from the old world and move to the new (to keep the net worth correct) - Done
		// If a player moves from a group to a world outside a group (or another group) then leave the balance in the last world.
		logIt("Old world name is: "+ event.getFrom().getName());
		logIt("New world name is: "+ player.getWorld().getName());
		logIt("Player's balance point 1 is "+econ.getBalance(player.getName()));
		// Initialize and retrieve the current balance of this player
		Double oldBalance = econ.getBalance(player.getName());
		Double newBalance = 0.0; // Always zero to start - in future change to a config value
		//player.sendMessage(ChatColor.GOLD + "You changed world!!");
		player.sendMessage(String.format("Your old world balance was %s", econ.format(oldBalance)));
		// Create a new player file if one does not exist
		playerFile = new File(getDataFolder() + "/userdata", player.getName() + ".yml");
		if (playerFile.exists()) {
	    	// Get the YAML file for this player
	    	try {
	    		players.load(playerFile);
	    	} catch (Exception e) {
	            e.printStackTrace();
	        }
		} else {
			playerFile.getParentFile().mkdirs(); // just make the directory
			// First time to change a world
		}
		// Save the old balance unless the player is moving within a group of worlds
		// If both these worlds are in the groups.yml file, they may be linked
		logIt("World groups data:\nevent.getFrom().getName()="+event.getFrom().getName().toString());
		logIt("player.getWorld().getName() = " + player.getWorld().getName().toString());
		logIt("worldgroups.get(player.getWorld().getName()) = " + worldgroups.get(player.getWorld().getName()));
		if (worldgroups.containsKey(event.getFrom().getName().toString()) && worldgroups.containsKey(player.getWorld().getName().toString())) {
			logIt("Check #1 : both worlds are in groups.yml");
			if (worldgroups.get(event.getFrom().getName()).equalsIgnoreCase(worldgroups.get(player.getWorld().getName()))) {
				logIt("Old world and new world are in the same group!");
				// Set the balance in the old world to zero
				players.set(event.getFrom().getName().toLowerCase() + ".money", 0.0);
				// Player keeps their current balance plus any money that is in this new world   				
			} else {
				// These worlds are not in the same group
				logIt("Old world and new world are NOT in the same group!");
				// Save the balance in the old world
				players.set(event.getFrom().getName().toLowerCase() + ".money", oldBalance);
				// Zero out our current balance - Vault does not allow balances to be set to an arbitrary amount
				econ.withdrawPlayer(player.getName(), oldBalance);
			}
		} else {
			logIt("Check #2");
			// At least one or both are not in the groups file. Therefore, they are NOT in the same group
			logIt("Old world and new world are NOT in the same group!");
			// Save the balance in the old world
			players.set(event.getFrom().getName().toLowerCase() + ".money", oldBalance);
			// Zero out our current balance - Vault does not allow balances to be set to an arbitrary amount
			econ.withdrawPlayer(player.getName(), oldBalance);
		}
		// Player's balance at this point should be zero 0.0
		logIt("Player's balance should be zero at point 2 is "+econ.getBalance(player.getName()));
		// Sort out the balance for the new world
		if (worldgroups.containsKey(player.getWorld().getName().toLowerCase())) {
			// The new world in is a group
			logIt("The new world is in a group");
			// Step through each world, apply the balance and zero out balances if they are not in that world
			String groupName = worldgroups.get(player.getWorld().getName().toLowerCase());
			logIt("Group name = " + groupName);
			// Get the name of each world in the group
			Set<String> keys = worldgroups.keySet();
			for (String key:keys) {
				logIt("World:" + key);
				if (worldgroups.get(key).equalsIgnoreCase(groupName)) {
					// The world is in the same group as this one
					logIt("The new world is in group "+groupName);
					newBalance = players.getDouble(key.toLowerCase() + ".money");
					logIt("Balance in world "+ key+ " = $"+newBalance);
					econ.depositPlayer(player.getName(), newBalance);
					// Zero out the old amount
					players.set(key.toLowerCase() + ".money", 0.0);
				}
			}
			logIt("Player's balance point 3 is "+econ.getBalance(player.getName()));
		} else {
			// This world is not in a group
			// Grab new balance from new world, if it exists, otherwise it is zero
			newBalance = players.getDouble((player.getWorld().getName().toLowerCase() + ".money"));
			// Apply new balance to player;
			econ.depositPlayer(player.getName(), newBalance);
			// If the new world is in a group, then take the value of all the worlds together
			logIt("Player's balance point 4 is "+econ.getBalance(player.getName()));
		}
		// Grab the message from the config file
		String newWorldMessage = config.getString("newworldmessage");
		if (newWorldMessage == null) {
			// Do not show any message
		} else try {
			player.sendMessage(String.format(ChatColor.GOLD + newWorldMessage, econ.format(econ.getBalance(player.getName()))));
		} catch (Exception e) {
			logIt("Error: New world message in config.yml is malformed. Use text and one %s for the balance only.\nExample: Your balance in this world is %s");
			player.sendMessage(String.format(ChatColor.GOLD + "Your balance in this world is %s", econ.format(econ.getBalance(player.getName()))));
		}
		// Write the balance to this world
		players.set(player.getWorld().getName().toLowerCase() + ".money", econ.getBalance(player.getName()));
		// Save the player file just in case there is a server problem
   		try {
			players.save(playerFile);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	private void firstRun() throws Exception {
	    if(!configFile.exists()){
	        configFile.getParentFile().mkdirs();
	        copy(getResource("config.yml"), configFile);
	    }
	}
	private void copy(InputStream in, File file) {
	    try {
	        OutputStream out = new FileOutputStream(file);
	        byte[] buf = new byte[1024];
	        int len;
	        while((len=in.read(buf))>0){
	            out.write(buf,0,len);
	        }
	        out.close();
	        in.close();
	    } catch (Exception e) {
	        e.printStackTrace();
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
 
    private boolean inWorldGroup(String world1, String world2) {
    	// Check to see if both these worlds are in groups.yml, if not, they are not in the same group
    	if (worldgroups.containsKey(world1) && worldgroups.containsKey(world2)) {
    		if (worldgroups.get(world1).equals(worldgroups.get(world2))) {
    			return true;
    		}
    	}
    	return false;
    }
    
    private void mwmDeposit(String playerName, Double amount, String world) {
    	FileConfiguration playersBalance = new YamlConfiguration();
    	Double oldBalance = 0.0;
		// Create a new player file if one does not exist
		playerFile = new File(getDataFolder() + "/userdata", playerName + ".yml");
		if (playerFile.exists()) {
	    	// Get the YAML file for this player and any deposit that may exist
	    	try {
	    		playersBalance.load(playerFile);
	    		oldBalance = playersBalance.getDouble(world.toLowerCase() + ".money");
	    	} catch (Exception e) {
	    		// If that world has no balance we use 0.0
	    	}
		}
		// logIt("Deposited " + econ.format(amount) + "\n to " + econ.format(oldBalance) + "in " + playerName + "'s account in world " + world);
		// Deposit
	    playersBalance.set(world.toLowerCase()+ ".money", amount + oldBalance);
	    try {
            playersBalance.save(playerFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void mwmWithdraw(String playerName, Double amount, String world) {
    	FileConfiguration playersBalance = new YamlConfiguration();
    	Double oldBalance = 0.0;
		// Create a new player file if one does not exist
		playerFile = new File(getDataFolder() + "/userdata", playerName + ".yml");
		if (playerFile.exists()) {
	    	// Get the YAML file for this player and any deposit that may exist
	    	try {
	    		playersBalance.load(playerFile);
	    		oldBalance = playersBalance.getDouble(world.toLowerCase() + ".money");
	    	} catch (Exception e) {
	    		// Return 0.0 if there is no record of that world
	    	}
		} 
		// logIt("Withdrew " + econ.format(amount) + "\n from " + econ.format(oldBalance) + " in " + playerName + "'s account in world " + world);
		// Withdraw
	    playersBalance.set(world.toLowerCase() + ".money", oldBalance - amount);
	    try {
            playersBalance.save(playerFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
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
		playersBalance.set(world.toLowerCase() + ".money", amount);
	    try {
            playersBalance.save(playerFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
      
    private double mwmBalance(String playerName, String world) {
    	// The database is file based so we need a file configuration object
       	FileConfiguration playersBalances = new YamlConfiguration();
    	Double balance = 0.0;
		// Create a new player file if one does not exist
		playerFile = new File(getDataFolder() + "/userdata", playerName + ".yml");
		if (playerFile.exists()) {
	    	// Get the YAML file for this player and any balance that may exist
	    	try {
	    		playersBalances.load(playerFile);
	    		balance = playersBalances.getDouble(world.toLowerCase() + ".money");
	    	} catch (Exception e) {
	    		// A balance for that world does not exist so we just return zero
	    	}
		}
		// logIt("Balance is " + econ.format(balance) + " in world " + world);
		return balance;
    }
    
    private void mwmSaveOfflineWorld(String playerName, String playerWorld) {
       	FileConfiguration playersBalance = new YamlConfiguration();
		// Create a new player file if one does not exist
		playerFile = new File(getDataFolder() + "/userdata", playerName + ".yml");
		if (playerFile.exists()) {
	    	// Get the YAML file for this player
	    	try {
	    		playersBalance.load(playerFile);
	    	} catch (Exception e) {	    		
	    	}
		}
		// Save the logout flag
		// Note, even if someone has a world called "offline_world" this will still work
	    playersBalance.set("offline_world.name", playerWorld.toLowerCase());
	    try {
            playersBalance.save(playerFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private String mwmReadOfflineWorld(String playerName) {
       	FileConfiguration playersBalance = new YamlConfiguration();
    	// Responds with the world that the player logged out in, if any otherwise null
		// Create a new player file if one does not exist
		playerFile = new File(getDataFolder() + "/userdata", playerName + ".yml");
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
    					OfflinePlayer op = Bukkit.getServer().getOfflinePlayer(args[1].toString());
    					double am = Double.parseDouble(args[2]);
    					String targetWorld = args[3].toLowerCase();
    			        if (op.hasPlayedBefore()){
    			            // We know this player, so check if the amount is a value
     			        		// Check the world is valid
    			        		if (Bukkit.getWorld(targetWorld) == null) {
    	   	   			        	sender.sendMessage(String.format(ChatColor.GOLD + targetWorld + " is an unknown world."));
            			        	return true;    			        		   			        			
    			        		} else {
    	   	   			        	// Find out if the player is offline or online
    	   	   			        	if (op.isOnline()) {
    	   	   			        		// logIt("Target is online");
    	   	   			        		
    	   	   			        		String recWorld = op.getPlayer().getWorld().getName();
    	   	   			        		// If the person is online and in the world that is the designated world or the same group, then just add to their balance
    	   	   			        		if (recWorld.equalsIgnoreCase(targetWorld) || inWorldGroup(targetWorld,recWorld)) {
    	   	   			        			logIt("Target is in target world or both worlds are in the same group");
    	   	   			        			// Zero out their previous balance
    	   	   			        			double pBalance = econ.getBalance(op.getName());
    	   	   			        			econ.withdrawPlayer(op.getName(), pBalance);
    	   	   			        			if (am>0.0) {
    	   	   			        				econ.depositPlayer(op.getName(), am);
    	   	   			        			} else if (am<0.0) {
    	   	   			        				econ.withdrawPlayer(op.getName(),am);
    	   	   			        			}
    	   	   			        		} else {
    	   	   			        			// They are in a totally different world. Add it to the MWM database
    	   	   			        			logIt("Target is not in the target world or group");
    	   	   			        			// Set the amount to the named player
    	   	   			        			mwmSet(op.getName(),am,targetWorld);
    	   	   			        		}
    	   	   			        	} else {
    	   	   			        		// Offline player - deposit the money in that world's account
     	   	   			        		// logIt("Target is offline");
     	   	   			        		// If the player logged out in the world where the payment is being sent then credit the economy
    	   	   			        		// Otherwise credit MWM account
     	   	   			        		String offlineWorld = mwmReadOfflineWorld(op.getName());
     	   	   			        		// If the player is offline and we do not know which world they were in when they logged off then we cannot accept payment
     	   	   			        		if (offlineWorld == null) {
     	   	   			        			sender.sendMessage(String.format(ChatColor.RED + "Sorry, you cannot set the balance for that offline player yet with MWM. They need to login at least one more time."));  	
     	   	   			        			return true;
     	   	   			        		}
     	   	   			        		if (offlineWorld.equalsIgnoreCase(targetWorld)) {
     	   	   			        			// Target's offline is the same as the pay-to world
     	   	   			        			double pBalance = econ.getBalance(op.getName());
     	   	   			        			econ.withdrawPlayer(op.getName(), pBalance);
	     	   	   			        		if (am>0.0) {
		   	   			        				econ.depositPlayer(op.getName(), am);
		   	   			        			} else if (am<0.0) {
		   	   			        				econ.withdrawPlayer(op.getName(),am);
		   	   			        			}
    	   	   			        		} else {
    	   	   			        			// Target's offline is different to the pay to world so set it via MWM
    	   	   			        			mwmSet(op.getName(),am,targetWorld);
    	   	   			        		}
    	   	   			        	}
    	   	   			        	sender.sendMessage(String.format(ChatColor.GOLD + "You set " + op.getName() + "'s balance to " + econ.format(am) + " in " + args[3]));
    	   	   			        	return true;    			        		
    			        		}
    			        	 
    			        }else{
    			        	sender.sendMessage(String.format(ChatColor.GOLD + op.getName() + " is not a recognised player"));
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
    					// Check if the player exists and can receive money
    					OfflinePlayer op = Bukkit.getServer().getOfflinePlayer(args[1].toString());
    					double am = Double.parseDouble(args[2]);
    					String targetWorld = args[3].toLowerCase();
    			        if (op.hasPlayedBefore()){
    			            // We know this player, so check if the amount is a value
    			        	if (am < 0.0) {
    	   			        	sender.sendMessage(String.format(ChatColor.GOLD + "Amounts should be positive."));
        			        	return true;    			        		
    			        	} else {
    			        		// Check the world is valid
    			        		if (Bukkit.getWorld(targetWorld) == null) {
    	   	   			        	sender.sendMessage(String.format(ChatColor.GOLD + targetWorld + " is an unknown world."));
            			        	return true;    			        		   			        			
    			        		} else {
    	   	   			        	// Find out if the player is offline or online
    	   	   			        	if (op.isOnline()) {
    	   	   			        		// logIt("Target is online");
    	   	   			        		
    	   	   			        		String recWorld = op.getPlayer().getWorld().getName();
    	   	   			        		// If the person is online and in the world that is the designated world or the same group, then just add to their balance
    	   	   			        		if (recWorld.equalsIgnoreCase(targetWorld) || inWorldGroup(targetWorld,recWorld)) {
    	   	   			        			// logIt("Target is in target world or both worlds are in the same group");
    	   	   			        			econ.withdrawPlayer(op.getPlayer().getName(), am);
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
    	   	   			        			econ.withdrawPlayer(op.getName(), am);
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
    			        	sender.sendMessage(String.format(ChatColor.GOLD + op.getName() + " is not a recognised player"));
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
    					// Check if the player exists and can receive money
    					OfflinePlayer op = Bukkit.getServer().getOfflinePlayer(args[1].toString());
    					double am = Double.parseDouble(args[2]);
    					String targetWorld = args[3].toLowerCase();
    			        if (op.hasPlayedBefore()){
    			            // We know this player, so check if the amount is a value
    			        	if (am < 0.0) {
    	   			        	sender.sendMessage(String.format(ChatColor.GOLD + "Amounts should be positive."));
        			        	return true;    			        		
    			        	} else {
    			        		// Check the world is valid
    			        		if (Bukkit.getWorld(targetWorld) == null) {
    	   	   			        	sender.sendMessage(String.format(ChatColor.GOLD + targetWorld + " is an unknown world."));
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
    	   	   			        			econ.depositPlayer(op.getPlayer().getName(), am);
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
    	   	   			        			econ.depositPlayer(op.getName(), am);
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
    			        	sender.sendMessage(String.format(ChatColor.GOLD + op.getName() + " is not a recognised player"));
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
    					// Check if the player exists and can receive money
    					OfflinePlayer op = Bukkit.getServer().getOfflinePlayer(args[1].toString());
    					double am = Double.parseDouble(args[2]);
    					String targetWorld = args[3].toLowerCase();
    			        if (op.hasPlayedBefore()){
    			            // We know this player, so check if the amount is a value
    			        	if (am < 0.0) {
    	   			        	sender.sendMessage(String.format(ChatColor.GOLD + "Amounts should be positive."));
        			        	return true;    			        		
    			        	} else {
    			        		// Check that the player has enough money
    			        		if (am > econ.getBalance(sender.getName())) {
    			        			sender.sendMessage(String.format(ChatColor.GOLD + "Sorry, you only have " + econ.format(econ.getBalance(sender.getName())) + " in this world."));
            			        	return true; 
    			        		}
    			        		// Check the world is valid
    			        		if (Bukkit.getWorld(targetWorld) == null) {
    	   	   			        	sender.sendMessage(String.format(ChatColor.GOLD + targetWorld + " is an unknown world."));
            			        	return true;    			        		   			        			
    			        		} else {
    	   	   			        	// Find out if the player is offline or online
    	   	   			        	if (op.isOnline()) {
    	   	   			        		// logIt("Target is online");
    	   	   			        		
    	   	   			        		String recWorld = op.getPlayer().getWorld().getName();
    	   	   			        		// If the person is online and in the world that is the designated world or the same group, then just add to their balance
    	   	   			        		if (recWorld.equalsIgnoreCase(targetWorld) || inWorldGroup(targetWorld,recWorld)) {
    	   	   			        			// logIt("Target is in target world or both worlds are in the same group");
    	   	   			        			econ.depositPlayer(op.getPlayer().getName(), am);
    	   	   			        			op.getPlayer().sendMessage(String.format(ChatColor.GOLD + sender.getName() + " paid you " + econ.format(am)));
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
    	   	   			        			econ.depositPlayer(op.getName(), am);
    	   	   			        		} else {
    	   	   			        			// logIt("Target's offline is different to the pay to world");
    	   	   			        			mwmDeposit(op.getName(),am,targetWorld);
    	   	   			        		}
    	   	   			        	}
    	   	   			        	sender.sendMessage(String.format(ChatColor.GOLD + "You paid " + econ.format(am) + " to " + op.getName() + " in " + args[3]));
    	   	   			        	// Deduct the amount from the player
    	   	   			        	econ.withdrawPlayer(sender.getName(), am);
    	   	   			        	return true;    			        		
    			        		}
    			        	} 
    			        }else{
    			        	sender.sendMessage(String.format(ChatColor.GOLD + op.getName() + " is not a recognised player"));
    			        	return true;
    			        }
    				}
    			// End Pay command
    			} else {
    				return false;
    			}
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
			String requestedPlayer = "";
			if (args.length == 0) {
				// Just the balance command
				requestedPlayer = sender.getName();
			} else {
				// Check admin permissions
				if (!sender.hasPermission("mwm.playerbalance")) {
					sender.sendMessage(String.format(ChatColor.RED + "You do not have permission to use that command."));
					return true;
				}
				// Check if the player exists 
				OfflinePlayer op = Bukkit.getServer().getOfflinePlayer(args[0].toString());
		        if (op.hasPlayedBefore()){
		        	requestedPlayer = args[0];
		        	if (!op.isOnline()) {
		        		offline = true;
		        	}
		        } else {
		        	sender.sendMessage(String.format(ChatColor.RED + "Unknown player."));
					return true;
		        }
		        sender.sendMessage(String.format(ChatColor.GOLD + requestedPlayer +"'s balance:"));
			}
    		// Find out where the player is
			String playerWorld = "";
			if (offline) {
				// Player is offline. Function returns null if there is no known MWM balance. In that case just return their economy balance
				playerWorld = mwmReadOfflineWorld(requestedPlayer);
				if (playerWorld == null) {
					// Return just the current balance
					sender.sendMessage(String.format(ChatColor.GOLD + econ.format(econ.getBalance(requestedPlayer))));
					return true;
				} else {
					playerWorld = playerWorld.toLowerCase();
				}
			} else {
				// Player is online
				playerWorld = sender.getServer().getPlayer(requestedPlayer).getWorld().getName().toLowerCase();
			}
    		// Look up details on that player
    		playerFile = new File(getDataFolder() + "/userdata", requestedPlayer + ".yml");
    		Double networth = 0.0;
	    	Double worldBalance = 0.0;
    		if (playerFile.exists()) {
    			//logIt("Player exists in MWM");
    	    	// The list of worlds in the player's file may not include the world they are in
    	    	// Start with the world they are in now and then add onto that based on what is in the MWM player file
    	    	// Set the current balance in MWM database
    	    	mwmSet(requestedPlayer,econ.getBalance(requestedPlayer),playerWorld);
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
    	    		//logIt("World in file = "+s);
    	    		// Ignore the world I am in
    	    		//if (s.equalsIgnoreCase(playerWorld)) {
    	    			//worldBalance = econ.getBalance(requestedPlayer);
    	    			//logIt("I am in this world and my balance is " + worldBalance);
    	    		//} else {
    	    			worldBalance = players.getDouble(s.toLowerCase()+".money");
    	    		//}
    	    		networth += worldBalance;
    	    		// Display balance in each world
    	    		// The line below can be used to grab all world names
    	    		// Collection<MultiverseWorld> wmList = core.getMVWorldManager().getMVWorlds();
    	    		// DEBUG
    	    		// TODO
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
    	    		// Only show positive balances
    	    		if (worldBalance > 0.0) {
    	    			sender.sendMessage(String.format(s + " " + ChatColor.GREEN + econ.format(worldBalance)));
    	    		} else if (worldBalance < 0.0) {
       	    			sender.sendMessage(String.format(s + " " + ChatColor.RED + econ.format(worldBalance)));
    	    		}
    	    	}
    	    	sender.sendMessage(String.format(ChatColor.GOLD + "Total balance across all worlds is " + econ.format(networth)));
    		} else {
    			//logIt("Player does not exists in MWM");
    			// The player has no MWM data so just show the current world's balance
    			worldBalance = econ.getBalance(requestedPlayer);
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
	    		if (worldBalance > 0.0) {
	    			sender.sendMessage(String.format(playerWorld + " " + ChatColor.GREEN + econ.format(worldBalance)));
	    		} else if (worldBalance < 0.0) {
   	    			sender.sendMessage(String.format(playerWorld + " " + ChatColor.RED + econ.format(worldBalance)));
	    		}
	    		sender.sendMessage(String.format(ChatColor.GOLD + "Total balance across all worlds is " + econ.format(worldBalance)));    			
    		}
    		return true;
    	} //If this has happened the function will return true. 
            // If this hasn't happened the a value of false will be returned.
    	return false; 
    }
}


