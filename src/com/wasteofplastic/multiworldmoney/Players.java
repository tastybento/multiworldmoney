package com.wasteofplastic.multiworldmoney;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class Players {
    private UUID uuid;
    private String name;
    private HashMap<String, Double> balances;
    private World logoffWorld;
    private MultiWorldMoney plugin;

    /**
     * Loads a player
     * @param player
     */
    public Players(MultiWorldMoney plugin, Player player) {
	this.plugin = plugin;
	balances = new HashMap<String, Double>();
	name = "";
	load(player);
    }
    /**
     * @return the uuid
     */
    public UUID getUuid() {
	return uuid;
    }
    /**
     * @param uuid the uuid to set
     */
    public void setUuid(UUID uuid) {
	this.uuid = uuid;
    }
    /**
     * @return the name
     */
    public String getName() {
	return name;
    }
    /**
     * @param name the name to set
     */
    public void setName(String name) {
	this.name = name;
    }

    /**
     * Get balance for a specific world. 
     * @param world
     * @return balance or 0 if there isn't one
     */
    public double getBalance(World world) {
	if (balances.containsKey(world.getName())) {
	    //plugin.getLogger().info("DEBUG: world balance found for world '"+world.getName()+"' = $" + balances.get(world.getName()));
	    return balances.get(world.getName());
	}
	//plugin.getLogger().info("DEBUG: world balance found for world '"+world.getName()+"' not found");
	return 0D;
    }

    /**
     * Set balance for a specific world
     * @param world
     * @param balance
     */
    public void setBalance(World world, double balance) { 
	//plugin.getLogger().info("DEBUG: Storing balance in " + world.getName() + " $" + balance);

	balances.put(world.getName(), balance);
    }

    /**
     * Deposits amount for a specific world
     * @param world
     * @param amount
     */
    public void deposit(World world, double amount) {
	if (balances.containsKey(world.getName())) {
	    balances.put(world.getName(), amount + balances.get(world.getName()));
	} else {
	    balances.put(world.getName(), amount);
	}
    }

    /**
     * Withdraws amount for a specific world
     * @param world
     * @param amount
     */
    public void withdraw(World world, double amount) {
	if (balances.containsKey(world.getName())) {
	    balances.put(world.getName(), balances.get(world.getName()) - amount);
	} else {
	    balances.put(world.getName(), -amount);
	}
    }
    /**
     * @return the logoffWorld
     */
    public World getLogoffWorld() {
	return logoffWorld;
    }
    /**
     * @param logoffWorld the logoffWorld to set
     */
    public void setLogoffWorld(World logoffWorld) {
	this.logoffWorld = logoffWorld;
    }

    /**
     * Saves the player
     */
    public void save() {
	File userFolder = new File(plugin.getDataFolder(),"players");
	if (!userFolder.exists()) {
	    userFolder.mkdir();
	}
	// Save the info on this player
	File user = new File(userFolder, uuid.toString() + ".yml");
	YamlConfiguration player = new YamlConfiguration();
	player.set("name", name);
	player.set("uuid", uuid.toString());
	player.set("logoffworld", logoffWorld.getName());
	// Save the balances
	for (String worldName : balances.keySet()) {
	    player.set("balances." + worldName, balances.get(worldName));
	}
	try {
	    player.save(user);
	} catch (IOException e) {
	    plugin.getLogger().severe("Could not save player file: " + user.getAbsolutePath());
	    e.printStackTrace();
	}
    }

    public void load(Player player) {
	// Get what we know
	name = player.getName();
	uuid = player.getUniqueId();
	//plugin.getLogger().info("DEBUG: loading player " + name);
	File userFolder = new File(plugin.getDataFolder(),"players");
	if (!userFolder.exists()) {
	    userFolder.mkdir();
	}
	// Load the info on this player
	File userFile = new File(userFolder, uuid.toString() + ".yml");
	YamlConfiguration playerConfig = new YamlConfiguration();
	if (userFile.exists()) { 
	    //plugin.getLogger().info("DEBUG: loading file ");
	    try {
		playerConfig.load(userFile);
		String logOutworld = playerConfig.getString("logoffworld", "");
		// Get the last world. Could be null if it's not recognized
		logoffWorld = plugin.getServer().getWorld(logOutworld);
		if (playerConfig.contains("balances")) {
		    //plugin.getLogger().info("DEBUG: Balances section exists");
		    // Load the balances
		    for (String world : playerConfig.getConfigurationSection("balances").getKeys(false)) {
			//plugin.getLogger().info("DEBUG: loading " + world);
			World balanceWorld = plugin.getServer().getWorld(world);
			if (balanceWorld != null) {
			    //plugin.getLogger().info("DEBUG: world exists - balance is " + playerConfig.getDouble("balances." + world));
			    setBalance(balanceWorld, playerConfig.getDouble("balances." + world, 0D));
			}
		    }
		}
	    } catch (FileNotFoundException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    } catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    } catch (InvalidConfigurationException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    }
	}

    }
}
