package com.betafish.auctionhouse;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class AuctionManager {
    private final JavaPlugin plugin;
    private final Economy econ;
    private final Map<String, Auction> auctions = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> pendingDeliveries = new HashMap<>();
    private final Map<UUID, List<Auction>> playerHistory = new HashMap<>();

    private final File dataFile;
    private final File historyFile;
    private final File categoriesFile;
    private YamlConfiguration data;
    private YamlConfiguration historyData;
    private YamlConfiguration categoriesData;

    public AuctionManager(JavaPlugin plugin, Economy econ) {
        this.plugin = plugin;
        this.econ = econ;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        this.historyFile = new File(plugin.getDataFolder(), "history.yml");
        this.categoriesFile = new File(plugin.getDataFolder(), "categories.yml");
    }

    public JavaPlugin getPlugin() { return this.plugin; }

    public void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (Exception e) { e.printStackTrace(); }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        if (!historyFile.exists()) {
            try { historyFile.createNewFile(); } catch (Exception e) { e.printStackTrace(); }
        }
        historyData = YamlConfiguration.loadConfiguration(historyFile);

        if (data.isConfigurationSection("auctions")) {
            for (String key : data.getConfigurationSection("auctions").getKeys(false)) {
                Map<String, Object> map = data.getConfigurationSection("auctions." + key).getValues(true);
                Auction a = Auction.deserialize(map);
                auctions.put(a.id, a);
            }
        }

        if (data.isConfigurationSection("deliveries")) {
            for (String s : data.getConfigurationSection("deliveries").getKeys(false)) {
                UUID u = UUID.fromString(s);
                List<ItemStack> list = (List<ItemStack>) data.get("deliveries." + s);
                pendingDeliveries.put(u, list == null ? new ArrayList<>() : list);
            }
        }

        // Load history per player
        if (historyData.isConfigurationSection("players")) {
            for (String uuidStr : historyData.getConfigurationSection("players").getKeys(false)) {
                UUID playerId = UUID.fromString(uuidStr);
                List<Map<String, Object>> playerAuctions = (List<Map<String, Object>>) historyData.get("players." + uuidStr);
                if (playerAuctions != null) {
                    List<Auction> auctions = new ArrayList<>();
                    for (Map<String, Object> map : playerAuctions) {
                        Auction a = Auction.deserialize(map);
                        auctions.add(a);
                    }
                    playerHistory.put(playerId, auctions);
                }
            }
        }

        // Load categories configuration
        if (!categoriesFile.exists()) {
            plugin.saveResource("categories.yml", false);
        }
        categoriesData = YamlConfiguration.loadConfiguration(categoriesFile);
    }

    public void save() {
        try {
            data = YamlConfiguration.loadConfiguration(dataFile);
            data.set("auctions", null);
            for (Auction a : auctions.values()) {
                data.set("auctions." + a.id, a.serialize());
            }

            data.set("deliveries", null);
            for (Map.Entry<UUID, List<ItemStack>> e : pendingDeliveries.entrySet()) {
                data.set("deliveries." + e.getKey().toString(), e.getValue());
            }

            data.save(dataFile);

            // Save history to separate file
            historyData = YamlConfiguration.loadConfiguration(historyFile);
            historyData.set("players", null);
            for (Map.Entry<UUID, List<Auction>> entry : playerHistory.entrySet()) {
                List<Map<String, Object>> playerAuctions = new ArrayList<>();
                for (Auction a : entry.getValue()) {
                    playerAuctions.add(a.serialize());
                }
                historyData.set("players." + entry.getKey().toString(), playerAuctions);
            }
            historyData.save(historyFile);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public Auction createAuction(UUID seller, ItemStack item, Auction.Type type, double price, long durationMillis) {
        return createAuction(seller, item, type, price, durationMillis, "General");
    }

    public Auction createAuction(UUID seller, ItemStack item, Auction.Type type, double price, long durationMillis, String category) {
        // Check max listings per player
        int maxListings = plugin.getConfig().getInt("max-listings-per-player", 5);
        long currentListings = auctions.values().stream()
                .filter(a -> a.seller.equals(seller))
                .count();
        if (currentListings >= maxListings) {
            throw new IllegalStateException("You have reached the maximum number of listings (" + maxListings + ")");
        }

        String id;
        Random rand = new Random();
        // generate a unique 4-digit zero-padded numeric id (0000-9999)
        do {
            id = String.format("%04d", rand.nextInt(10000));
        } while (auctions.containsKey(id));
        long end = System.currentTimeMillis() + durationMillis;
        Auction a = new Auction(id, seller, item.clone(), type, price, end);
        a.category = category; // Set the category
        if (type == Auction.Type.AUCTION) a.currentBid = 0.0;
        auctions.put(id, a);

        // Add to seller's history immediately when created
        playerHistory.computeIfAbsent(seller, k -> new ArrayList<>()).add(a);

        save();
        return a;
    }

    public Collection<Auction> listAuctions() { return auctions.values(); }

    public Auction getAuction(String id) { return auctions.get(id); }

    public void removeAuction(String id) { auctions.remove(id); save(); }

    public void addPendingDelivery(UUID player, ItemStack item) {
        pendingDeliveries.computeIfAbsent(player, k -> new ArrayList<>()).add(item);
        save();
    }

    public void deliverPending(Player p) {
        List<ItemStack> items = pendingDeliveries.remove(p.getUniqueId());
        if (items != null && !items.isEmpty()) {
            for (ItemStack it : items) {
                p.getInventory().addItem(it);
            }
            p.sendMessage("[Auction] You received pending auction items.");
            save();
        }
    }

    public void processExpired() {
        long now = System.currentTimeMillis();
        List<Auction> ended = new ArrayList<>();
        for (Auction a : auctions.values()) {
            if (a.endTime <= now) ended.add(a);
        }
        for (Auction a : ended) {
            handleEnd(a);
            auctions.remove(a.id);
        }
        if (!ended.isEmpty()) save();
    }

    /**
     * Force-end an auction immediately (admin use).
     * @param id auction id
     * @return true if auction was found and ended, false otherwise
     */
    public boolean forceEnd(String id) {
        Auction a = auctions.remove(id);
        if (a == null) return false;
        handleEnd(a);
        save();
        return true;
    }

    private void handleEnd(Auction a) {
        // Add to seller's history
        playerHistory.computeIfAbsent(a.seller, k -> new ArrayList<>()).add(a);

        // Add to bidder's history if there was a bidder
        if (a.currentBidder != null) {
            playerHistory.computeIfAbsent(a.currentBidder, k -> new ArrayList<>()).add(a);
        }

        if (a.type == Auction.Type.FIXED) {
            // fixed price expired: return to seller
            Player seller = Bukkit.getPlayer(a.seller);
            if (seller != null && seller.isOnline()) {
                seller.getInventory().addItem(a.item);
                seller.sendMessage("[Auction] Your listing expired and item was returned.");
            } else {
                addPendingDelivery(a.seller, a.item);
            }
        } else {
            // auction - if has bid, winner already precharged
            if (a.currentBidder != null) {
                // give item to winner
                Player winner = Bukkit.getPlayer(a.currentBidder);
                if (winner != null && winner.isOnline()) {
                    winner.getInventory().addItem(a.item);
                    winner.sendMessage("[Auction] You won auction " + a.id + " and received your item.");
                } else {
                    addPendingDelivery(a.currentBidder, a.item);
                }
                // pay seller
                econ.depositPlayer(Bukkit.getOfflinePlayer(a.seller), a.currentBid);
                OfflineMessage(a.seller, "Your item sold for " + a.currentBid);
            } else {
                // no bids, return to seller
                Player seller = Bukkit.getPlayer(a.seller);
                if (seller != null && seller.isOnline()) {
                    seller.getInventory().addItem(a.item);
                    seller.sendMessage("[Auction] Your auction ended with no bids; item returned.");
                } else {
                    addPendingDelivery(a.seller, a.item);
                }
            }
        }
    }

    private void OfflineMessage(UUID sellerId, String msg) {
        Player p = Bukkit.getPlayer(sellerId);
        if (p != null && p.isOnline()) p.sendMessage("[Auction] " + msg);
    }

    public List<Auction> getPlayerHistory(UUID playerId) {
        return playerHistory.getOrDefault(playerId, new ArrayList<>());
    }

    public List<Auction> getAllHistory() {
        List<Auction> allHistory = new ArrayList<>();
        for (List<Auction> playerAuctions : playerHistory.values()) {
            allHistory.addAll(playerAuctions);
        }
        return allHistory;
    }

    public Economy getEconomy() { return econ; }

    // Category management methods
    public List<String> getCategoryNames() {
        List<String> categoryNames = new ArrayList<>();
        if (categoriesData.isConfigurationSection("categories")) {
            for (String key : categoriesData.getConfigurationSection("categories").getKeys(false)) {
                String name = categoriesData.getString("categories." + key + ".name");
                if (name != null) {
                    categoryNames.add(name);
                }
            }
        }
        return categoryNames;
    }

    public String getCategoryItem(String categoryName) {
        if (categoriesData.isConfigurationSection("categories")) {
            for (String key : categoriesData.getConfigurationSection("categories").getKeys(false)) {
                String name = categoriesData.getString("categories." + key + ".name");
                if (categoryName.equals(name)) {
                    return categoriesData.getString("categories." + key + ".item", "CHEST");
                }
            }
        }
        return "CHEST"; // default fallback
    }

    public Map<String, String> getAllCategories() {
        Map<String, String> categories = new LinkedHashMap<>();
        if (categoriesData.isConfigurationSection("categories")) {
            for (String key : categoriesData.getConfigurationSection("categories").getKeys(false)) {
                String name = categoriesData.getString("categories." + key + ".name");
                String item = categoriesData.getString("categories." + key + ".item", "CHEST");
                if (name != null) {
                    categories.put(name, item);
                }
            }
        }
        return categories;
    }
}
