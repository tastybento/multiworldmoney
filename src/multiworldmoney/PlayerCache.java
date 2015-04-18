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

public class PlayerCache {
    private MultiWorldMoney plugin;
    private HashMap<UUID, Players> cache; 
    private HashMap<String, UUID> names; 
    private HashMap<String, UUID> lowerCaseNames;
    private HashMap<UUID, String> reverseNames;

    public PlayerCache(MultiWorldMoney plugin) {
	// Initialize
	this.plugin = plugin;
	this.cache = new HashMap<UUID, Players>();
	this.names = new HashMap<String, UUID>();
	this.lowerCaseNames = new HashMap<String, UUID>();
	this.reverseNames = new HashMap<UUID, String>();
	// Load the name file
	File namesFile = new File(plugin.getDataFolder(), "names.yml");
	YamlConfiguration nameConfig = new YamlConfiguration();
	if (namesFile.exists()) {
	    try {
		nameConfig.load(namesFile);
	    } catch (FileNotFoundException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    } catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    } catch (InvalidConfigurationException e) {
		plugin.getLogger().severe("names.yml file is corrupt!");
		e.printStackTrace();
	    }
	    // Transfer names into the names, lowercasenames and reverseNames hashmaps
	    for (String key : nameConfig.getKeys(false)) {
		//plugin.getLogger().info("DEBUG: loading player name " + key);
		// Each key is a UUID followed by a name
		try {
		    UUID uuid = UUID.fromString(key);
		    names.put(nameConfig.getString(key), uuid);
		    lowerCaseNames.put(nameConfig.getString(key).toLowerCase(),uuid);
		    reverseNames.put(uuid, nameConfig.getString(key));
		} catch (Exception e) {
		    plugin.getLogger().severe("Could not load UUID " + key + " from names.yml. Skipping...");
		}
	    }
	}
    }

    /**
     * Add player to the cache
     * @param player
     */
    public void addPlayer(Player player) {
	//plugin.getLogger().info("DEBUG: Add player called");
	// Check for new name
	if (reverseNames.containsKey(player.getUniqueId())) {
	    //plugin.getLogger().info("DEBUG: UUID is known");
	    // UUID is known
	    if (!reverseNames.get(player.getUniqueId()).equals(player.getName())) {
		//plugin.getLogger().info("DEBUG: new name for this UUID");
		// New name for this UUID
		String oldName = reverseNames.get(player.getUniqueId());
		reverseNames.put(player.getUniqueId(), player.getName());
		names.remove(oldName);
		names.put(player.getName(), player.getUniqueId());
		lowerCaseNames.remove(oldName.toLowerCase());
		lowerCaseNames.put(player.getName().toLowerCase(), player.getUniqueId());
	    } // else do nothing
	} else {
	    //plugin.getLogger().info("DEBUG: New UUID");
	    // New UUID
	    if (names.containsKey(player.getName()) || lowerCaseNames.containsKey(player.getName().toLowerCase())) {
		// Name exists, but UUID did not exist in the reverseNames list.
		// This means that a player is now taking over this name
		// This person now owns this name. Well done! Best to give a small warning
		plugin.getLogger().warning("New UUID [" + player.getUniqueId().toString() + "] has taken over name : " + player.getName());
	    }
	    names.put(player.getName(), player.getUniqueId());
	    lowerCaseNames.put(player.getName().toLowerCase(), player.getUniqueId());
	    reverseNames.put(player.getUniqueId(), player.getName());
	}
	// Add to the cache
	if (!cache.containsKey(player.getUniqueId())) {
	    //plugin.getLogger().info("DEBUG: Adding " + player.getName() + " to cache");
	    Players newPlayer = new Players(plugin, player);
	    cache.put(player.getUniqueId(), newPlayer);
	}
    }

    /**
     * Saves the players name list
     */
    public void savePlayerNames() {
	// Save the names file
	File namesFile = new File(plugin.getDataFolder(), "names.yml");
	YamlConfiguration nameConfig = new YamlConfiguration();
	for (String name : names.keySet()) {
	    //plugin.getLogger().info("DEBUG: saving name " + name);
	    nameConfig.set(names.get(name).toString(), name);
	}
	try {
	    nameConfig.save(namesFile);
	} catch (IOException e) {
	    plugin.getLogger().severe("Could not save names.yml");
	    e.printStackTrace();
	}
    }

    /**
     * Removes player from cache
     * @param player
     */
    public void removePlayer(Player player) {
	if (cache.containsKey(player.getUniqueId())) {
	    // Set the logout world
	    cache.get(player.getUniqueId()).setLogoffWorld(player.getWorld());
	    // Set the balance for their final world
	    cache.get(player.getUniqueId()).setBalance(player.getWorld(), VaultHelper.econ.getBalance(player));
	    // save player and remove from cache
	    cache.get(player.getUniqueId()).save();
	    cache.remove(player.getUniqueId());
	    // Their name remains in the name list forever...
	}
    }

    /**
     * Deposits amount for player in world
     * @param player
     * @param world
     * @param amount
     */
    public void deposit(Player player, World world, double amount) {
	//plugin.getLogger().info("DEBUG: Deposit " + player.getName() + " in " + world.getName() + " to $" + amount);
	addPlayer(player);
	cache.get(player.getUniqueId()).deposit(world, plugin.roundDown(amount,2));
    }

    /**
     * Get balance for player in world
     * @param player
     * @param world
     * @return
     */
    public double getBalance(Player player, World world) {
	addPlayer(player);
	return cache.get(player.getUniqueId()).getBalance(world);
    }

    /**
     * Get a name from a UUID. Returns null if unknown.
     * @param playerUUID
     * @return name
     */
    public String getName(UUID playerUUID) {
	return reverseNames.get(playerUUID);
    }

    /**
     * Get a UUID from a string. Returns null if unknown.
     * Automatically tries lower case as well as upper case characters.
     * Player must have played at least once to be known.
     * @param name
     * @return UUID
     */
    public UUID getUUID(String name) {
	// lower case names take precedence over mixed case
	if (lowerCaseNames.containsKey(name)) {
	    return lowerCaseNames.get(name);
	}
	return names.get(name);
    }

    /**
     * Set player balance in world
     * @param player
     * @param world
     * @param balance
     */
    public void setBalance(Player player, World world, double balance) {
	//plugin.getLogger().info("DEBUG: Setting balance " + player.getName() + " in " + world.getName() + " to $" + balance);
	addPlayer(player);
	cache.get(player.getUniqueId()).setBalance(world, plugin.roundDown(balance,2));
    }

    /**
     * Withdraw amount from player in world
     * @param player
     * @param world
     * @param amount
     */
    public void withdraw(Player player, World world, double amount) {
	//plugin.getLogger().info("DEBUG: Withdrawing " + player.getName() + " in " + world.getName() + " to $" + amount);
	addPlayer(player);
	cache.get(player.getUniqueId()).withdraw(world, plugin.roundDown(amount,2));
    }

    /**
     * Get the log off world
     * @param playerUUID
     * @return World or null
     */
    public World getLogOutWorld(UUID playerUUID) {
	// This has to be obtained from the player's file
	File userFolder = new File(plugin.getDataFolder(),"players");
	if (!userFolder.exists()) {
	    userFolder.mkdir();
	}
	// Load the info on this player
	File userFile = new File(userFolder, playerUUID.toString() + ".yml");
	YamlConfiguration playerConfig = new YamlConfiguration();
	if (userFile.exists()) { 
	    //plugin.getLogger().info("DEBUG: loading file "); 
	    try {
		playerConfig.load(userFile);
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
	    String logOutworld = playerConfig.getString("logoffworld", "");
	    // Get the last world. Could be null if it's not recognized
	    return plugin.getServer().getWorld(logOutworld);
	}
	return null;
    }

    /**
     * Adds a name/UUID pair to the database
     * @param name
     * @param uuid
     */
    public void addName(String name, UUID uuid) {
	names.put(name, uuid);
	lowerCaseNames.put(name.toLowerCase(), uuid);
	reverseNames.put(uuid, name);
    }
}
