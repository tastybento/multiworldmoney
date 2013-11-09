/*
 * Copyright 2013 Ben Gibbs. All rights reserved.
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
 * It provides a way, independent of economy to keep balances separate    	*
 * between worlds.															*
 ****************************************************************************
 
 */

package com.wasteofplastic.multiworldmoney;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

import net.milkbowl.vault.economy.Economy;

//import com.onarandombox.MultiverseCore.MultiverseCore;
//import com.onarandombox.MultiverseCore.api.MultiverseWorld;
//import com.onarandombox.MultiverseCore.utils.WorldManager;








import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
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
    // This is the main event handler of this plugin. It adjusts balances when a player changes world
	@EventHandler(ignoreCancelled = true)
	public void onWorldLoad(PlayerChangedWorldEvent event) {
		// Find out who is moving world
		Player player = event.getPlayer();
		
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
	    		// Save the old balance
	    		players.set(event.getFrom().getName() + ".money", oldBalance);
	    		// Zero out our current balance - Vault does not allow balances to be set to an arbitrary amount
	    		// I have some concerns about race conditions here. Technically a user could be given money in this process
	    		econ.withdrawPlayer(player.getName(), oldBalance);
	    		// Grab new balance from new world, if it exists, otherwise it is zero
	    		newBalance = players.getDouble((player.getWorld().getName() + ".money"));
	    		// Apply new balance to player;
	    		econ.depositPlayer(player.getName(), newBalance);
	    		players.save(playerFile);
	    		// Tell the user
	    		player.sendMessage(String.format(ChatColor.GOLD + "Your balance in this world is %s", econ.format(newBalance)));
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
	    		player.sendMessage(String.format(ChatColor.GOLD + "Your balance in this world is %s", econ.format(0.0)));
			} else {
				// Tell the user
				player.sendMessage(String.format(ChatColor.GOLD + "Your balance in this world is %s", econ.format(oldBalance)));
			}
    		// Save it
    		try {
    			players.save(playerFile);
    		} catch (Exception e) {
    			e.printStackTrace();
    		}
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
        } catch (Exception e) {
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
            config.save(configFile); //saves the FileConfiguration to its File
            //groups.save(groupsFile);
            players.save(playerFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
 
    
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
    	if(cmd.getName().equalsIgnoreCase("balance")){ // If the player typed /networth then do the following...
    		// Find out who sent the command
    		String requestedPlayer = sender.getName();
    		// Find out where I am
    		String playerWorld = sender.getServer().getPlayer(requestedPlayer).getWorld().getName();
    		//getLogger().info("Default player =" + requestedPlayer + " in " + playerWorld);
    		// Check if there is a player name associated with the command
    		/*
    		 * if (args[0] != "") {
    		
    			requestedPlayer = args[0];
    		}*/
    		//getLogger().info();
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
    	    		String newName = core.getMVWorldManager().getMVWorld(s).getAlias();
    	    		if (newName != null) {
    	    			s = newName;
    	    		}
    	    		sender.sendMessage(String.format(s + " " + ChatColor.GOLD + econ.format(worldBalance)));
    	    	}
    	    	sender.sendMessage(String.format(ChatColor.GOLD + "Total balance is " + econ.format(networth)));
    		}
    		return true;
    	} //If this has happened the function will return true. 
            // If this hasn't happened the a value of false will be returned.
    	return false; 
    }
}


