package com.betafish.auctionhouse;

import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LostAuction extends JavaPlugin {
    private static LostAuction instance;
    private Economy econ;
    private AuctionManager auctionManager;
    private File logDir;

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

        logDir = new File(getDataFolder(), "logs");
        if (!logDir.exists()) logDir.mkdirs();

        new Metrics(this, 32291);

        auctionManager = new AuctionManager(this, econ);
        auctionManager.load();

        AuctionCommand auctionCommand = new AuctionCommand(auctionManager);
        getCommand("auction").setExecutor(auctionCommand);
        getCommand("auction").setTabCompleter(auctionCommand);

        AuctionAdminCommand adminCommand = new AuctionAdminCommand(auctionManager);
        getCommand("auctionadmin").setExecutor(adminCommand);
        getCommand("auctionadmin").setTabCompleter(adminCommand);

        AuctionGUI gui = new AuctionGUI(auctionManager);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(new AnvilListener(auctionManager), this);
        getServer().getPluginManager().registerEvents(new JoinListener(auctionManager), this);

        int interval = getConfig().getInt("check-interval-seconds", 60);
        Bukkit.getScheduler().runTaskTimer(this, () -> auctionManager.processExpired(), 20L * interval, 20L * interval);

        getLogger().info("LostAuction enabled");
        logDebug("Plugin enabled");
    }

    @Override
    public void onDisable() {
        if (auctionManager != null) auctionManager.save();
        getLogger().info("LostAuction disabled");
        logDebug("Plugin disabled");
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

    public void logDebug(String msg) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String hour = new SimpleDateFormat("yyyy-MM-dd-HH").format(new Date());
                File logFile = new File(logDir, "debug-" + hour + ".log");
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)))) {
                    out.println("[" + timestamp + "] " + msg);
                }
            } catch (Exception e) {
                getLogger().warning("Could not write debug log: " + e.getMessage());
            }
        });
    }

    public static LostAuction getInstance() {
        return instance;
    }

    public Economy getEconomy() {
        return econ;
    }
}
