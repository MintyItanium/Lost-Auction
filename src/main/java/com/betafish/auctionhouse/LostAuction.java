package com.betafish.auctionhouse;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;

public class LostAuction extends JavaPlugin {
    private static LostAuction instance;
    private Economy econ;
    private AuctionManager auctionManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        fillMissingConfig();
        if (!setupEconomy()) {
            getLogger().severe("Vault and EssentialsX are required. Plugin disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        auctionManager = new AuctionManager(this, econ);
        auctionManager.load();

        AuctionCommand auctionCommand = new AuctionCommand(auctionManager);
        getCommand("auction").setExecutor(auctionCommand);
        getCommand("auction").setTabCompleter(auctionCommand);

        AuctionAdminCommand adminCommand = new AuctionAdminCommand(auctionManager);
        getCommand("auctionadmin").setExecutor(adminCommand);
        getCommand("auctionadmin").setTabCompleter(adminCommand);

        getServer().getPluginManager().registerEvents(new AuctionGUI(auctionManager), this);
        getServer().getPluginManager().registerEvents(new AnvilListener(auctionManager), this);
        getServer().getPluginManager().registerEvents(new JoinListener(auctionManager), this);

        int interval = getConfig().getInt("check-interval-seconds", 60);
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> auctionManager.processExpired(), 20L * interval, 20L * interval);

        getLogger().info("LostAuction enabled");
    }

    @Override
    public void onDisable() {
        if (auctionManager != null) auctionManager.save();
        getLogger().info("LostAuction disabled");
    }

    private void fillMissingConfig() {
        FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(getResource("config.yml")));
        getConfig().setDefaults(defaultConfig);
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    public static LostAuction getInstance() {
        return instance;
    }

    public Economy getEconomy() {
        return econ;
    }
}
