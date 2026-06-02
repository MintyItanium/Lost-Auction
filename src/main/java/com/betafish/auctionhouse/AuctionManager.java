package com.betafish.auctionhouse;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionManager {
    private final JavaPlugin plugin;
    private final Economy econ;
    private final Map<String, Auction> auctions = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> pendingDeliveries = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> reclaimableItems = new ConcurrentHashMap<>();
    private final Set<UUID> autoClaimPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final File dataFile;
    private final File historyFile;
    private final File categoriesFile;
    private final Object historyLock = new Object();
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

        if (data.isConfigurationSection("reclaims")) {
            for (String s : data.getConfigurationSection("reclaims").getKeys(false)) {
                UUID u = UUID.fromString(s);
                List<ItemStack> list = (List<ItemStack>) data.get("reclaims." + s);
                reclaimableItems.put(u, list == null ? new ArrayList<>() : list);
            }
        }

        // Load categories configuration
        if (!categoriesFile.exists()) {
            plugin.saveResource("categories.yml", false);
        }
        categoriesData = YamlConfiguration.loadConfiguration(categoriesFile);

        if (data.isList("auto-claim")) {
            for (String s : data.getStringList("auto-claim")) {
                try { autoClaimPlayers.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
            }
        }

        // Migrate old pendingDeliveries to reclaimableItems for players without auto-claim
        if (!pendingDeliveries.isEmpty()) {
            List<UUID> toRemove = new ArrayList<>();
            for (Map.Entry<UUID, List<ItemStack>> entry : pendingDeliveries.entrySet()) {
                UUID playerId = entry.getKey();
                if (!autoClaimPlayers.contains(playerId)) {
                    List<ItemStack> items = entry.getValue();
                    reclaimableItems.merge(playerId, items, (oldList, newList) -> {
                        oldList.addAll(newList);
                        return oldList;
                    });
                    toRemove.add(playerId);
                }
            }
            for (UUID uuid : toRemove) pendingDeliveries.remove(uuid);
            if (pendingDeliveries.isEmpty()) data.set("deliveries", null);
            save();
        }
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

            data.set("reclaims", null);
            for (Map.Entry<UUID, List<ItemStack>> e : reclaimableItems.entrySet()) {
                data.set("reclaims." + e.getKey().toString(), e.getValue());
            }

            List<String> autoClaimList = new ArrayList<>();
            for (UUID uuid : autoClaimPlayers) autoClaimList.add(uuid.toString());
            data.set("auto-claim", autoClaimList);

            data.save(dataFile);
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

        save();
        return a;
    }

    public Collection<Auction> listAuctions() { return auctions.values(); }

    public Auction getAuction(String id) { return auctions.get(id); }

    public void removeAuction(String id) { auctions.remove(id); save(); }

    public boolean isBuyItNowEnabled() {
        return plugin.getConfig().getBoolean("buy-it-now-enabled", true);
    }

    public boolean buyItNow(Player buyer, Auction a) {
        if (!isBuyItNowEnabled()) return false;
        double price = a.startingPrice;
        if (!econ.has(buyer, price)) return false;
        econ.withdrawPlayer(buyer, price);
        econ.depositPlayer(Bukkit.getOfflinePlayer(a.seller), price);

        // deliver item
        HashMap<Integer, ItemStack> overflow = buyer.getInventory().addItem(stripCategoryLore(a.item));
        for (ItemStack drop : overflow.values()) {
            buyer.getWorld().dropItemNaturally(buyer.getLocation(), drop);
        }

        // record buyer so history shows "Sold" instead of "Expired"
        a.currentBidder = buyer.getUniqueId();
        a.currentBid = price;

        // record in history for both
        addToHistory(a.seller, a);
        addToHistory(buyer.getUniqueId(), a);

        auctions.remove(a.id);
        save();
        return true;
    }

    public void addPendingDelivery(UUID player, ItemStack item) {
        pendingDeliveries.computeIfAbsent(player, k -> new ArrayList<>()).add(item);
        save();
    }

    public void addReclaimableItem(UUID player, ItemStack item) {
        reclaimableItems.computeIfAbsent(player, k -> new ArrayList<>()).add(item.clone());
        save();
    }

    public List<ItemStack> getReclaimableItems(UUID player) {
        return reclaimableItems.getOrDefault(player, new ArrayList<>());
    }

    public boolean hasReclaimableItems(UUID player) {
        List<ItemStack> items = reclaimableItems.get(player);
        return items != null && !items.isEmpty();
    }

    public void removeReclaimableItem(UUID player, int index) {
        List<ItemStack> items = reclaimableItems.get(player);
        if (items != null && index >= 0 && index < items.size()) {
            items.remove(index);
            if (items.isEmpty()) reclaimableItems.remove(player);
            save();
        }
    }

    public boolean toggleAutoClaim(UUID player) {
        if (autoClaimPlayers.contains(player)) {
            autoClaimPlayers.remove(player);
            save();
            return false;
        } else {
            autoClaimPlayers.add(player);
            save();
            return true;
        }
    }

    public boolean hasAutoClaim(UUID player) {
        return autoClaimPlayers.contains(player);
    }

    public static ItemStack stripCategoryLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return item;
        List<String> lore = meta.getLore();
        if (lore == null) return item;
        lore.removeIf(line -> line.startsWith("CATEGORY:"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void deliverPending(Player p) {
        List<ItemStack> items = pendingDeliveries.remove(p.getUniqueId());
        if (items != null && !items.isEmpty()) {
            for (ItemStack it : items) {
                java.util.HashMap<Integer, ItemStack> overflow = p.getInventory().addItem(it);
                for (ItemStack drop : overflow.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }
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
     * Force-end an auction immediately (/auctionadmin).
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

    public boolean changeBid(String id, double newBid) {
        Auction a = auctions.get(id);
        if (a == null) return false;
        if (a.type == Auction.Type.FIXED) {
            a.startingPrice = newBid;
        } else {
            if (a.currentBidder != null) {
                econ.depositPlayer(Bukkit.getOfflinePlayer(a.currentBidder), a.currentBid);
            }
            a.currentBid = newBid;
            a.currentBidder = null;
        }
        save();
        return true;
    }

    public boolean renewAuction(String id) {
        Auction a = auctions.get(id);
        if (a == null) return false;
        long duration = plugin.getConfig().getLong("default-duration-hours", 24) * 3600L * 1000L;
        a.endTime = System.currentTimeMillis() + duration;
        save();
        return true;
    }

    public boolean removeBid(String id) {
        Auction a = auctions.get(id);
        if (a == null || a.currentBidder == null) return false;
        econ.depositPlayer(Bukkit.getOfflinePlayer(a.currentBidder), a.currentBid);
        MessagePlayer(a.currentBidder, "Your bid on auction " + a.id + " was removed. Refunded " + a.currentBid);
        a.currentBid = 0;
        a.currentBidder = null;
        save();
        return true;
    }

    public boolean deleteAuction(String id) {
        Auction a = auctions.remove(id);
        if (a == null) return false;
        save();
        return true;
    }

    private void handleEnd(Auction a) {
        // Add to seller's history
        addToHistory(a.seller, a);

        // Add to bidder's history if there was a bidder
        if (a.currentBidder != null) {
            addToHistory(a.currentBidder, a);
        }

        if (a.type == Auction.Type.FIXED) {
            if (hasAutoClaim(a.seller)) {
                addPendingDelivery(a.seller, stripCategoryLore(a.item));
                MessagePlayer(a.seller, "Your listing expired. Item will be delivered when you rejoin.");
            } else {
                addReclaimableItem(a.seller, stripCategoryLore(a.item));
                MessagePlayer(a.seller, "Your listing expired. Claim your item back from the Auction House!");
            }
        } else {
            if (a.currentBidder != null) {
                Player winner = Bukkit.getPlayer(a.currentBidder);
                if (winner != null && winner.isOnline()) {
                    winner.getInventory().addItem(stripCategoryLore(a.item));
                    winner.sendMessage("[Auction] You won auction " + a.id + " and received your item.");
                } else {
                    addPendingDelivery(a.currentBidder, stripCategoryLore(a.item));
                }
                econ.depositPlayer(Bukkit.getOfflinePlayer(a.seller), a.currentBid);
                MessagePlayer(a.seller, "Your item sold for " + a.currentBid);
            } else {
                if (hasAutoClaim(a.seller)) {
                    addPendingDelivery(a.seller, stripCategoryLore(a.item));
                    MessagePlayer(a.seller, "Your auction ended with no bids. Item will be delivered when you rejoin.");
                } else {
                    addReclaimableItem(a.seller, stripCategoryLore(a.item));
                    MessagePlayer(a.seller, "Your auction ended with no bids. Claim your item back from the Auction House!");
                }
            }
        }
    }

    private void MessagePlayer(UUID sellerId, String msg) {
        Player p = Bukkit.getPlayer(sellerId);
        if (p != null && p.isOnline()) p.sendMessage("[Auction] " + msg);
    }

    private void addToHistory(UUID playerId, Auction a) {
        synchronized (historyLock) {
            historyData = YamlConfiguration.loadConfiguration(historyFile);
            String path = "players." + playerId.toString();
            List<Map<String, Object>> list = (List<Map<String, Object>>) historyData.get(path);
            if (list == null) list = new ArrayList<>();
            list.add(a.serialize());
            historyData.set(path, list);
            saveHistoryAsync();
        }
    }

    private void saveHistoryAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (historyLock) {
                try {
                    historyData.save(historyFile);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    public List<Auction> getPlayerHistory(UUID playerId) {
        synchronized (historyLock) {
            historyData = YamlConfiguration.loadConfiguration(historyFile);
            String path = "players." + playerId.toString();
            List<Map<String, Object>> list = (List<Map<String, Object>>) historyData.get(path);
            if (list == null) return new ArrayList<>();
            List<Auction> result = new ArrayList<>();
            for (Map<String, Object> map : list) {
                result.add(Auction.deserialize(map));
            }
            Collections.reverse(result);
            return result;
        }
    }

    public List<Auction> getAllHistory() {
        synchronized (historyLock) {
            historyData = YamlConfiguration.loadConfiguration(historyFile);
            List<Auction> allHistory = new ArrayList<>();
            if (historyData.isConfigurationSection("players")) {
                for (String uuidStr : historyData.getConfigurationSection("players").getKeys(false)) {
                    List<Map<String, Object>> list = (List<Map<String, Object>>) historyData.get("players." + uuidStr);
                    if (list != null) {
                        for (Map<String, Object> map : list) {
                            allHistory.add(Auction.deserialize(map));
                        }
                    }
                }
            }
            Collections.reverse(allHistory);
            return allHistory;
        }
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
