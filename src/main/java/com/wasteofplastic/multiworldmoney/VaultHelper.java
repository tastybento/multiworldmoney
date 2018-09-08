package com.wasteofplastic.multiworldmoney;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

/**
 * Helper class for Vault Economy and Permissions
 */
class VaultHelper {
    private Economy econ = null;
    private Permission permission = null;

    /**
     * Sets up the economy instance
     *
     * @return true if successful
     */
    public boolean setupEconomy() {
        RegisteredServiceProvider<Economy> economyProvider = Bukkit.getServer().getServicesManager()
                .getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyProvider != null) {
            econ = economyProvider.getProvider();
        }
        return econ != null;
    }

    /**
     * Sets up the permissions instance
     *
     * @return true if successful
     */
    public boolean setupPermissions() {
        RegisteredServiceProvider<Permission> permissionProvider = Bukkit.getServer().getServicesManager()
                .getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null) {
            permission = permissionProvider.getProvider();
        }
        return (permission != null);
    }

    /**
     * @return the econ
     */
    public Economy getEcon() {
        return econ;
    }

    /**
     * @return the permission
     */
    public Permission getPermission() {
        return permission;
    }

    /**
     * Checks permission of player in world or in any world
     *
     * @param player player
     * @param perm permission string
     * @return true if player has permission
     */
    public boolean checkPerm(final Player player, final String perm) {
        return !permission.has(player, perm);
    }

    /**
     * Adds permission to player
     *
     * @param player player
     * @param perm permission string
     */
    public void addPerm(final Player player, final String perm) {
        permission.playerAdd(player, perm);
    }

    /**
     * Removes a player's permission
     *
     * @param player player
     * @param perm permission string
     */
    public void removePerm(final Player player, final String perm) {
        permission.playerRemove(player, perm);
    }

}