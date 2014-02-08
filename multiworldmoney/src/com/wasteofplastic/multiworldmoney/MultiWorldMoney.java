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
    File configFile;
    File playerFile;
    //File groupsFile;
    FileConfiguration config;
    FileConfiguration players;
    //FileConfiguration groups;
    World worldDefault;
    private static HashMap<String,String> worldgroups = new HashMap<String,String>();
    //World worldDefault = getServer().getWorlds().get(0);
    //String defaultWorld = worldDefault.getName();
    String defaultWorld = "world";
    
   
    @Override
    public void onDisable() {
        // Save all our yamls
        saveYamls();
    }
    
    @Override
	public void onEnable() {
    	//getLogger().info("onEnable has been invoked!");
	    configFile = new File(getDataFolder(), "config.yml");
	    playerFile = new File(getDataFolder() + "/userdata", "temp"); // not a real file
	    //groupsFile = new File(getDataFolder(), "groups.yml");
	    
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
        players = new YamlConfiguration();
        //groups = new YamlConfiguration();
        loadYamls();
        
        // Find out the default world and insert into the config
        if (config.getString("defaultWorld") == null) {
        	// Nothing in the config
        	config.set("defaultWorld",defaultWorld);
        	saveYamls();
        } else {
        	// Grab the new default world from config.yml
        	defaultWorld = config.getString("defaultWorld");
        }
        
    	// Send stats
    	try {
    	    MetricsLite metrics = new MetricsLite(this);
    	    metrics.start();
    	} catch (IOException e) {
    	    // Failed to submit the stats :-(
    	}
    	

        
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
        	this.core = mvCore;
        	return true;
        }
    }
    
    // When a player logs in, we need to update their balance with any money that is in any world that is in the same group
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLogin(PlayerJoinEvent event) {
    	Player player = event.getPlayer();
    	String playerWorld = player.getWorld().getName();
    	String playerName = player.getName();
    	// getLogger().info(playerName + " logged in to " + playerWorld + " and they have " + econ.getBalance(playerName));
    	// Go through each world and if they are in the same group, grab that world's balance and add it to the player's current balance
    	for (String world: worldgroups.keySet()) {
    		// getLogger().info("Loop value = " + world);
    		if (inWorldGroup(world, playerWorld) && !world.equals(playerWorld)) {
    			// Remove any money from the world in the group
    			econ.depositPlayer(playerName, mwmBalance(playerName,world));
    			mwmSet(playerName,0.0,world);
    		}
    	}
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
		// Check to see if they are in a grouped world
		// If new world is in same group as old world, move the money from one world to the other
		// If player moves from a world outside a group into a group, then add up all the balances in that group, zero them out and give them to the player and set the new world balance to be the total
		// If a player moves within a group, then zero out from the old world and move to the new (to keep the net worth correct) - Done
		// If a player moves from a group to a world outside a group (or another group) then leave the balance in the last world.
		//getLogger().info("Old world name is: "+ event.getFrom().getName());
		//getLogger().info("New world name is: "+ player.getWorld().getName());
		//getLogger().info("Player's balance point 1 is "+econ.getBalance(player.getName()));
		// Initialize and retrieve the current balance of this player
		Double oldBalance = econ.getBalance(player.getName());
		Double newBalance = 0.0; // Always zero to start - in future change to a config value
		//player.sendMessage(ChatColor.GOLD + "You changed world!!");
		//player.sendMessage(String.format("Your old world balance was %s", econ.format(oldBalance)));
		// Create a new player file if one does not exist
		playerFile = new File(getDataFolder() + "/userdata", player.getName() + ".yml");
		if (playerFile.exists()) {
	    	// Get the YAML file for this player
	    	try {
	    		players.load(playerFile);
	    		// Save the old balance unless the player is moving within a group of worlds
	    		// If both these worlds are in the groups.yml file, they may be linked
	    		if (worldgroups.containsKey(event.getFrom().getName()) && worldgroups.containsKey(player.getWorld().getName())) {
	    			if (worldgroups.get(event.getFrom().getName()).equalsIgnoreCase(worldgroups.get(player.getWorld().getName()))) {
	    				// getLogger().info("Old world and new world are in the same group!");
	    				// Set the balance in the old world to zero
	    				players.set(event.getFrom().getName() + ".money", 0.0);
	    				// Player keeps their current balance plus any money that is in this new world   				
	    			} else {
		    			// These worlds are not in the same group
		    			//getLogger().info("Old world and new world are NOT in the same group!");
	    				// Save the balance in the old world
	    				players.set(event.getFrom().getName() + ".money", oldBalance);
	    	    		// Zero out our current balance - Vault does not allow balances to be set to an arbitrary amount
	    	    		econ.withdrawPlayer(player.getName(), oldBalance);
	    			}
	    		} else {
	    			// At least one or both are not in the groups file. Therefore, they are NOT in the same group
	    			//getLogger().info("Old world and new world are NOT in the same group!");
    				// Save the balance in the old world
    				players.set(event.getFrom().getName() + ".money", oldBalance);
    	    		// Zero out our current balance - Vault does not allow balances to be set to an arbitrary amount
    	    		econ.withdrawPlayer(player.getName(), oldBalance);
	    		}
	    		// Player's balance at this point should be zero 0.0
	    		//getLogger().info("Player's balance should be zero at point 2 is "+econ.getBalance(player.getName()));
	    		// Sort out the balance for the new world
	    		if (worldgroups.containsKey(player.getWorld().getName())) {
	    			// The new world in is a group
	    			//getLogger().info("The new world is in a group");
	    			// Step through each world, apply the balance and zero out balances if they are not in that world
	    			String groupName = worldgroups.get(player.getWorld().getName());
	    			//getLogger().info("Group name = " + groupName);
	    			// Get the name of each world in the group
	    			Set<String> keys = worldgroups.keySet();
	    			for (String key:keys) {
	    				//getLogger().info("World:" + key);
	    				if (worldgroups.get(key).equals(groupName)) {
	    					// The world is in the same group as this one
	    					//getLogger().info("The new world is in group "+groupName);
	    					newBalance = players.getDouble(key + ".money");
	    					//getLogger().info("Balance in world "+ key+ " = $"+newBalance);
	    					econ.depositPlayer(player.getName(), newBalance);
	    					// Zero out the old amount
	    					players.set(key + ".money", 0.0);
	    				}
	    			}
	    			//getLogger().info("Player's balance point 3 is "+econ.getBalance(player.getName()));
	    		} else {
	    			// This world is not in a group
		    		// Grab new balance from new world, if it exists, otherwise it is zero
		    		newBalance = players.getDouble((player.getWorld().getName() + ".money"));
		    		// Apply new balance to player;
		    		econ.depositPlayer(player.getName(), newBalance);
	    		// If the new world is in a group, then take the value of all the worlds together
		    		//getLogger().info("Player's balance point 4 is "+econ.getBalance(player.getName()));
	    		}
	    	} catch (Exception e) {
	            e.printStackTrace();
	        }
		} else {
			playerFile.getParentFile().mkdirs(); // just make the directory
			// First time to change a world
			// Find out if they are going to the default world. If so, they keep their balance.
			//getLogger().info("New world name is: "+ player.getWorld().getName());
			if (player.getWorld().getName() != defaultWorld) {
				//getLogger().info("Not going to the default world");
				// We want to keep money in the default world if this is the case
	    		// Save their balance in the default world
	    		players.set(defaultWorld + ".money", oldBalance);
	    		// Zero out our current balance
	    		econ.withdrawPlayer(player.getName(), oldBalance);
	    		//getLogger().info("Player's balance point 5 is "+econ.getBalance(player.getName()));
			} 
 	    }
		// Tell the user
		player.sendMessage(String.format(ChatColor.GOLD + "Your balance in this world is %s", econ.format(econ.getBalance(player.getName()))));
		// Write the balance to this world
		players.set(player.getWorld().getName() + ".money", econ.getBalance(player.getName()));
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
	    //if(!playerFile.exists()){
	    //    playerFile.getParentFile().mkdirs(); // just make the directory - maybe not needed
	    //}
	    //if(!groupsFile.exists()){
	    //    groupsFile.getParentFile().mkdirs();
	    //    copy(getResource("groups.yml"), groupsFile);
	    //}
	    
	    worldDefault = getServer().getWorlds().get(0);
	    defaultWorld = worldDefault.getName();
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
    
    public static void loadGroups() {
        YamlConfiguration groups = loadYamlFile("groups.yml");
        if(groups == null) {
            //MultiInv.log.info("No groups.yml found. Creating example file...");
            groups = new YamlConfiguration();
            
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
    public void saveYamls() {
        try {
        	// Config and groups are not changed yet, but it doesn't matter to save them
            //config.save(configFile); //saves the FileConfiguration to its File
            //groups.save(groupsFile);
            players.save(playerFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
 
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
    	Double oldBalance = 0.0;
		// Create a new player file if one does not exist
		playerFile = new File(getDataFolder() + "/userdata", playerName + ".yml");
		if (playerFile.exists()) {
	    	// Get the YAML file for this player and any deposit that may exist
	    	try {
	    		players.load(playerFile);
	    		oldBalance = players.getDouble(world + ".money");
	    	} catch (Exception e) {	    		
	    	}
		}
		// getLogger().info("Deposited " + econ.format(amount) + "\n to " + econ.format(oldBalance) + "in " + playerName + "'s account in world " + world);
		// Deposit
	    players.set(world + ".money", amount + oldBalance);
	    saveYamls();
    }

    private void mwmWithdraw(String playerName, Double amount, String world) {
    	Double oldBalance = 0.0;
		// Create a new player file if one does not exist
		playerFile = new File(getDataFolder() + "/userdata", playerName + ".yml");
		if (playerFile.exists()) {
	    	// Get the YAML file for this player and any deposit that may exist
	    	try {
	    		players.load(playerFile);
	    		oldBalance = players.getDouble(world + ".money");
	    	} catch (Exception e) {	    		
	    	}
		} 
		// getLogger().info("Withdrew " + econ.format(amount) + "\n from " + econ.format(oldBalance) + " in " + playerName + "'s account in world " + world);
		// Withdraw
	    players.set(world + ".money", oldBalance - amount);
	    saveYamls();
    }
    
    private void mwmSet(String playerName, Double amount, String world) {
		// Create a new player file if one does not exist
		playerFile = new File(getDataFolder() + "/userdata", playerName + ".yml");
		// getLogger().info("Set balance to " + econ.format(amount) + " for " + playerName + " in world " + world);
		// Set
	    players.set(world + ".money", amount);
	    saveYamls();
    }
      
    private double mwmBalance(String playerName, String world) {
    	Double balance = 0.0;
		// Create a new player file if one does not exist
		playerFile = new File(getDataFolder() + "/userdata", playerName + ".yml");
		if (playerFile.exists()) {
	    	// Get the YAML file for this player and any balance that may exist
	    	try {
	    		players.load(playerFile);
	    		balance = players.getDouble(world + ".money");
	    	} catch (Exception e) {	    		
	    	}
		}
		// getLogger().info("Balance is " + econ.format(balance) + " in world " + world);
		return balance;
    }
    
    private void mwmSaveOfflineWorld(String playerName, String playerWorld) {
		// Create a new player file if one does not exist
		playerFile = new File(getDataFolder() + "/userdata", playerName + ".yml");
		if (playerFile.exists()) {
	    	// Get the YAML file for this player
	    	try {
	    		players.load(playerFile);
	    	} catch (Exception e) {	    		
	    	}
		}
		// Save the logout flag
		// Note, even if someone has a world called "offline_world" this will still work
	    players.set("offline_world.name", playerWorld);
	    saveYamls();  
    }
    
    private String mwmReadOfflineWorld(String playerName) {
    	// Responds with the world that the player logged out in, if any otherwise null
		// Create a new player file if one does not exist
		playerFile = new File(getDataFolder() + "/userdata", playerName + ".yml");
		if (playerFile.exists()) {
	    	// Get the YAML file for this player
	    	try {
	    		players.load(playerFile);
	    		return players.getString("offline_world.name");
	    	} catch (Exception e) {	
	    	}
		}
		// The player does not have a file, and therefore does not have an offline world
		return null;
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
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
    				sender.sendMessage(String.format(ChatColor.GOLD + "/mwm reload - Reloads the groups.yml file"));
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
	    				// Reload groups
	    				loadGroups();
	  			        sender.sendMessage(String.format(ChatColor.GOLD + "MultiWorldMoney groups reloaded"));
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
    	   	   			        		// getLogger().info("Target is online");
    	   	   			        		
    	   	   			        		String recWorld = op.getPlayer().getWorld().getName();
    	   	   			        		// If the person is online and in the world that is the designated world or the same group, then just add to their balance
    	   	   			        		if (recWorld.equals(targetWorld) || inWorldGroup(targetWorld,recWorld)) {
    	   	   			        			// getLogger().info("Target is in target world or both worlds are in the same group");
    	   	   			        			// Zero out their previous balance
    	   	   			        			double pBalance = econ.getBalance(op.getName());
    	   	   			        			econ.withdrawPlayer(op.getName(), pBalance);
    	   	   			        			econ.depositPlayer(op.getName(), am);
    	   	   			        		} else {
    	   	   			        			// They are in a totally different world. Add it to the MWM database
    	   	   			        			// getLogger().info("Target is not in the target world or group");
    	   	   			        			// Set the amount to the named player
    	   	   			        			mwmSet(op.getName(),am,targetWorld);
    	   	   			        		}
    	   	   			        	} else {
    	   	   			        		// Offline player - deposit the money in that world's account
     	   	   			        		// getLogger().info("Target is offline");
     	   	   			        		// If the player logged out in the world where the payment is being sent then credit the economy
    	   	   			        		// Otherwise credit MWM account
     	   	   			        		String offlineWorld = mwmReadOfflineWorld(op.getName());
     	   	   			        		// If the player is offline and we do not know which world they were in when they logged off then we cannot accept payment
     	   	   			        		if (offlineWorld == null) {
     	   	   			        			sender.sendMessage(String.format(ChatColor.RED + "Sorry, you cannot set the balance for that offline player yet with MWM. They need to login at least one more time."));  	
     	   	   			        			return true;
     	   	   			        		}
     	   	   			        		if (offlineWorld.equalsIgnoreCase(targetWorld)) {
     	   	   			        			// getLogger().info("Target's offline is the same as the pay to world");
     	   	   			        			double pBalance = econ.getBalance(op.getName());
     	   	   			        			econ.withdrawPlayer(op.getName(), pBalance);
    	   	   			        			econ.depositPlayer(op.getName(), am);
    	   	   			        		} else {
    	   	   			        			// getLogger().info("Target's offline is different to the pay to world");
    	   	   			        			mwmSet(op.getName(),am,targetWorld);
    	   	   			        		}
    	   	   			        	}
    	   	   			        	sender.sendMessage(String.format(ChatColor.GOLD + "You set " + op.getName() + "'s balance to " + econ.format(am) + " in " + args[3]));
    	   	   			        	return true;    			        		
    			        		}
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
    	   	   			        		// getLogger().info("Target is online");
    	   	   			        		
    	   	   			        		String recWorld = op.getPlayer().getWorld().getName();
    	   	   			        		// If the person is online and in the world that is the designated world or the same group, then just add to their balance
    	   	   			        		if (recWorld.equals(targetWorld) || inWorldGroup(targetWorld,recWorld)) {
    	   	   			        			// getLogger().info("Target is in target world or both worlds are in the same group");
    	   	   			        			econ.withdrawPlayer(op.getPlayer().getName(), am);
    	       	   	   			        } else {
    	   	   			        			// They are in a totally different world. Take it from the MWM database
    	   	   			        			// getLogger().info("Target is not in the target world or group");
    	   	   			        			// Withdraw the amount to the named player
    	   	   			        			mwmWithdraw(op.getName(),am,targetWorld);
    	   	   			        		}
    	   	   			        	} else {
    	   	   			        		// Offline player - withdraw the money in that world's account
     	   	   			        		// getLogger().info("Target is offline");
     	   	   			        		// If the player logged out in the world where the payment is being sent then debit the economy
    	   	   			        		// Otherwise credit MWM account
     	   	   			        		String offlineWorld = mwmReadOfflineWorld(op.getName());
     	   	   			        		// If the player is offline and we do not know which world they were in when they logged off then we cannot accept payment
     	   	   			        		if (offlineWorld == null) {
     	   	   			        			sender.sendMessage(String.format(ChatColor.RED + "Sorry, you cannot debit that offline player yet. They need to login at least one more time."));  	
     	   	   			        			return true;
     	   	   			        		}
     	   	   			        		if (offlineWorld.equalsIgnoreCase(targetWorld)) {
     	   	   			        			// getLogger().info("Target's offline is the same as the pay to world");
    	   	   			        			econ.withdrawPlayer(op.getName(), am);
    	   	   			        		} else {
    	   	   			        			// getLogger().info("Target's offline is different to the pay to world");
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
    	   	   			        		// getLogger().info("Target is online");
    	   	   			        		
    	   	   			        		String recWorld = op.getPlayer().getWorld().getName();
    	   	   			        		// If the person is online and in the world that is the designated world or the same group, then just add to their balance
    	   	   			        		if (recWorld.equals(targetWorld) || inWorldGroup(targetWorld,recWorld)) {
    	   	   			        			// getLogger().info("Target is in target world or both worlds are in the same group");
    	   	   			        			econ.depositPlayer(op.getPlayer().getName(), am);
    	   	   			        		} else {
    	   	   			        			// They are in a totally different world. Add it to the MWM database
    	   	   			        			// getLogger().info("Target is not in the target world or group");
    	   	   			        			// Deposit the amount to the named player
    	   	   			        			mwmDeposit(op.getName(),am,targetWorld);
    	   	   			        		}
    	   	   			        	} else {
    	   	   			        		// Offline player - deposit the money in that world's account
     	   	   			        		// getLogger().info("Target is offline");
     	   	   			        		// If the player logged out in the world where the payment is being sent then credit the economy
    	   	   			        		// Otherwise credit MWM account
     	   	   			        		String offlineWorld = mwmReadOfflineWorld(op.getName());
     	   	   			        		// If the player is offline and we do not know which world they were in when they logged off then we cannot accept payment
     	   	   			        		if (offlineWorld == null) {
     	   	   			        			sender.sendMessage(String.format(ChatColor.RED + "Sorry, you cannot pay that offline player yet. They need to login at least one more time."));  	
     	   	   			        			return true;
     	   	   			        		}
     	   	   			        		if (offlineWorld.equalsIgnoreCase(targetWorld)) {
     	   	   			        			// getLogger().info("Target's offline is the same as the pay to world");
    	   	   			        			econ.depositPlayer(op.getName(), am);
    	   	   			        		} else {
    	   	   			        			// getLogger().info("Target's offline is different to the pay to world");
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
    	   	   			        		// getLogger().info("Target is online");
    	   	   			        		
    	   	   			        		String recWorld = op.getPlayer().getWorld().getName();
    	   	   			        		// If the person is online and in the world that is the designated world or the same group, then just add to their balance
    	   	   			        		if (recWorld.equals(targetWorld) || inWorldGroup(targetWorld,recWorld)) {
    	   	   			        			// getLogger().info("Target is in target world or both worlds are in the same group");
    	   	   			        			econ.depositPlayer(op.getPlayer().getName(), am);
    	   	   			        			op.getPlayer().sendMessage(String.format(ChatColor.GOLD + sender.getName() + " paid you " + econ.format(am)));
    	   	   			        		} else {
    	   	   			        			// They are in a totally different world. Add it to the MWM database
    	   	   			        			// getLogger().info("Target is not in the target world or group");
    	   	   			        			// Deposit the amount to the named player
    	   	   			        			mwmDeposit(op.getName(),am,targetWorld);
    	   	   			        		}
    	   	   			        	} else {
    	   	   			        		// Offline player - deposit the money in that world's account
     	   	   			        		// getLogger().info("Target is offline");
     	   	   			        		// If the player logged out in the world where the payment is being sent then credit the economy
    	   	   			        		// Otherwise credit MWM account
     	   	   			        		String offlineWorld = mwmReadOfflineWorld(op.getName());
     	   	   			        		// If the player is offline and we do not know which world they were in when they logged off then we cannot accept payment
     	   	   			        		if (offlineWorld == null) {
     	   	   			        			sender.sendMessage(String.format(ChatColor.RED + "Sorry, you cannot pay that offline player yet. They need to login at least one more time."));  	
     	   	   			        			return true;
     	   	   			        		}
     	   	   			        		if (offlineWorld.equalsIgnoreCase(targetWorld)) {
     	   	   			        			// getLogger().info("Target's offline is the same as the pay to world");
    	   	   			        			econ.depositPlayer(op.getName(), am);
    	   	   			        		} else {
    	   	   			        			// getLogger().info("Target's offline is different to the pay to world");
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
		        	sender.sendMessage(String.format(ChatColor.RED + "[MWM] Unknown player."));
					return true;
		        }
			}
    		// Find out where the player is
			String playerWorld = "";
			if (offline) {
				// Player is offline. Function returns null if there is no known MWM balance. In that case just return their economy balance
				playerWorld = mwmReadOfflineWorld(requestedPlayer);
				if (playerWorld == null) {
					// Return just the current balance
					sender.sendMessage(String.format(ChatColor.GOLD + "[MWM] " + econ.format(econ.getBalance(requestedPlayer))));
					return true;
				}
			} else {
				// Player is online
				playerWorld = sender.getServer().getPlayer(requestedPlayer).getWorld().getName();
			}
    		// Look up details on that player
    		playerFile = new File(getDataFolder() + "/userdata", requestedPlayer + ".yml");
    		if (playerFile.exists()) {
    			// The player exists
    			players = new YamlConfiguration();
    	    	// Get the YAML file for this player
    	    	try {
    	    		players.load(playerFile);
    	    	} catch (Exception e) {
    	            e.printStackTrace();
    	        }
    	    	// Step through file and print out balances for each world and total at the end
    	    	Double networth = 0.0;
    	    	Double worldBalance = 0.0;
    	    	Set<String> worldList = players.getKeys(false);
    	    	for (String s: worldList) {
    	    		//getLogger().info("World in file = "+s);
    	    		// Ignore the world I am in
    	    		if (s.equals(playerWorld)) {
    	    			worldBalance = econ.getBalance(requestedPlayer);
    	    			//getLogger().info("I am in this world and my balance is " + worldBalance);
    	    		} else {
    	    			worldBalance = players.getDouble(s+".money");
    	    		}
    	    		networth += worldBalance;
    	    		// Display balance in each world
    	    		// The line below can be used to grab all world names
    	    		// Collection<MultiverseWorld> wmList = core.getMVWorldManager().getMVWorlds();
    	    		try {
    	    			String newName = core.getMVWorldManager().getMVWorld(s).getAlias();
    	    			s = newName;
    	    		} catch (Exception e) {
    	    			// Multiverse does not know about this world, so don't show the alias
    	    		}
    	    		// Only show positive balances
    	    		if (worldBalance > 0.0) {
    	    			sender.sendMessage(String.format(s + " " + ChatColor.GREEN + econ.format(worldBalance)));
    	    		} else if (worldBalance < 0.0) {
       	    			sender.sendMessage(String.format(s + " " + ChatColor.RED + econ.format(worldBalance)));
    	    		}
    	    	}
    	    	sender.sendMessage(String.format(ChatColor.GOLD + "Total across all worlds is " + econ.format(networth)));
    		}
    		return true;
    	} //If this has happened the function will return true. 
            // If this hasn't happened the a value of false will be returned.
    	return false; 
    }
}


