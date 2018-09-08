package com.wasteofplastic.multiworldmoney;

import org.bukkit.ChatColor;

class Lang {

    private final MultiWorldMoney plugin;
    public static String setHelp;
    public static String deposited;
    public static String increasedBalance;
    public static String welcome;
    public static String error;
    public static String noPermission;
    public static String reloaded;
    public static String noPlayer;
    public static String unknownAmount;
    public static String unknownWorld;
    public static String balanceFor;
    public static String total;
    public static String offlineBalance;
    public static String setBalanceTo;
    public static String yourBalanceSetTo;
    public static String amountPositive;
    public static String withdrew;
    public static String reduceBalance;
    public static String reloadHelp;
    public static String takeHelp;
    public static String giveHelp;
    public static String playerHelp;
    public static String balanceHelp;
    public static String worldHelp;
    public static String amountHelp;
    public static String youCannotPayYourself;
    public static String payHelp;
    public static String sendTo;
    public static String receiveFrom;
    public static String insufficientFunds;
    /**
     * @param plugin - plugin
     */
    public Lang(MultiWorldMoney plugin) {
        this.plugin = plugin;
        loadLocale();
    }

    private void loadLocale() {
        // Load the defaults
        amountHelp = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("amountHelp", "<amount>"));
        amountPositive = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("amountMustBePositive", "Amount must be positive."));
        balanceFor = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("balanceFor", "Balance for [name]"));
        balanceHelp = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("balanceHelp", "<balance>"));
        deposited = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("deposited", "Deposited [amount] to [name] in [world]"));
        error = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("error", "Error:"));
        giveHelp = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("giveHelp", "Gives amount to player in world"));
        increasedBalance = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("increasedBalance", "Your balance increased by [amount] in [world]"));
        noPermission = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("noPermission", "You do not have permission to do that."));
        noPlayer = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("noPlayer", "Player not found."));
        offlineBalance = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("offlineBalance", "Balance for offline player [balance]"));
        playerHelp = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("playerHelp", "<player>"));
        receiveFrom = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("receiveFrom", "[amount] has been received from [name] in [world]"));
        reduceBalance = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("reduceBalance", "Your balance descreased by [amount] in [world]"));
        reloaded = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("reloaded","Reloaded configuration."));
        reloadHelp = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("reloadHelp","Reloads the MWM config"));
        sendTo = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("sendTo", "[amount] has been sent to [name] in [world]"));
        setBalanceTo = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("setBalanceTo", "Set [name]'s balance to [amount] in [world]"));
        setHelp = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("setHelp", "Sets the player's balance in world"));
        takeHelp = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("takeHelp", "Takes amount from player in world"));
        total = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("total", "Total : [total]"));
        unknownAmount = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("unknownAmount", "Unknown amount."));
        unknownWorld = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("unknownWorld", "Unknown world."));
        withdrew = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("withdrew", "Withdrew [amount] from [name] in [world]"));
        worldHelp = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("worldHelp", "<world>"));
        yourBalanceSetTo = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("yourBalanceSetTo", "Your balance was set to [amount] in [world]"));
        youCannotPayYourself = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("youCannotPayYourself", "You cannot pay yourself."));
        payHelp = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("payHelp", "Pays another player from your balance"));
        insufficientFunds = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("insufficientFunds", "You do not have sufficient funds."));    
    }
}
