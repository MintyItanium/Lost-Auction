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
import java.time.Instant;
import java.util.*;
import java.util.Base64;

public class AuctionManager {
    private final JavaPlugin plugin;
    private final Economy econ;
    private final Map<String, Auction> auctions = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> pendingDeliveries = new HashMap<>();
    private final Map<UUID, List<ItemStack>> reclaimableItems = new HashMap<>();
    private final Set<UUID> autoClaimPlayers = new HashSet<>();
    private final Map<String, PurchaseRecord> recentPurchases = new LinkedHashMap<>();
    private final Set<UUID> newListingNotifications = new HashSet<>();

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

    static class PurchaseRecord {
        final String auctionId;
        final UUID buyer;
        final UUID seller;
        final byte[] itemBytes;
        final double price;
        long timestamp;

        PurchaseRecord(String auctionId, UUID buyer, UUID seller, ItemStack item, double price) {
            this.auctionId = auctionId;
            this.buyer = buyer;
            this.seller = seller;
            this.itemBytes = item.serializeAsBytes();
            this.price = price;
            this.timestamp = System.currentTimeMillis();
        }

        Map<String, Object> serialize() {
            Map<String, Object> m = new HashMap<>();
            m.put("auctionId", auctionId);
            m.put("buyer", buyer.toString());
            m.put("seller", seller.toString());
            m.put("itemBytes", Base64.getEncoder().encodeToString(itemBytes));
            m.put("price", price);
            m.put("timestamp", timestamp);
            return m;
        }

        static PurchaseRecord deserialize(Map<String, Object> m) {
            String auctionId = (String) m.get("auctionId");
            UUID buyer = UUID.fromString((String) m.get("buyer"));
            UUID seller = UUID.fromString((String) m.get("seller"));
            byte[] itemBytes = Base64.getDecoder().decode((String) m.get("itemBytes"));
            ItemStack item = ItemStack.deserializeBytes(itemBytes);
            double price = ((Number) m.get("price")).doubleValue();
            PurchaseRecord r = new PurchaseRecord(auctionId, buyer, seller, item, price);
            if (m.containsKey("timestamp")) r.timestamp = ((Number) m.get("timestamp")).longValue();
            return r;
        }
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
                List<ItemStack> list = new ArrayList<>();
                List<?> raw = data.getList("deliveries." + s);
                if (raw != null) {
                    for (Object obj : raw) {
                        if (obj instanceof ItemStack) {
                            list.add((ItemStack) obj);
                        } else if (obj instanceof String) {
                            ItemStack item = itemFromBase64((String) obj);
                            if (item != null) list.add(item);
                        }
                    }
                }
                pendingDeliveries.put(u, list);
            }
        }

        if (data.isConfigurationSection("reclaims")) {
            for (String s : data.getConfigurationSection("reclaims").getKeys(false)) {
                UUID u = UUID.fromString(s);
                List<ItemStack> list = new ArrayList<>();
                List<?> raw = data.getList("reclaims." + s);
                if (raw != null) {
                    for (Object obj : raw) {
                        if (obj instanceof ItemStack) {
                            list.add((ItemStack) obj);
                        } else if (obj instanceof String) {
                            ItemStack item = itemFromBase64((String) obj);
                            if (item != null) list.add(item);
                        }
                    }
                }
                reclaimableItems.put(u, list);
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

        if (data.isConfigurationSection("purchases")) {
            for (String key : data.getConfigurationSection("purchases").getKeys(false)) {
                Map<String, Object> pm = data.getConfigurationSection("purchases." + key).getValues(true);
                PurchaseRecord pr = PurchaseRecord.deserialize(pm);
                long timeout = plugin.getConfig().getLong("undo-purchase-time-minutes", 10) * 60L * 1000L;
                if (System.currentTimeMillis() - pr.timestamp < timeout) {
                    recentPurchases.put(pr.auctionId, pr);
                }
            }
        }

        if (data.isList("new-listing-notifications")) {
            for (String s : data.getStringList("new-listing-notifications")) {
                try { newListingNotifications.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
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

    private List<String> itemToBase64(ItemStack item) {
        List<String> result = new ArrayList<>();
        try {
            result.add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            return result;
        } catch (Exception ex) {
            int max = item.getType().getMaxStackSize();
            int remaining = item.getAmount();
            List<ItemStack> split = new ArrayList<>();
            while (remaining > 0) {
                ItemStack part = item.clone();
                int amt = Math.min(remaining, max);
                part.setAmount(amt);
                split.add(part);
                remaining -= amt;
            }
            plugin.getLogger().warning("Split item " + item.getType() + " (" + item.getAmount() + ") into " + split.size() + " stacks of " + max);
            for (ItemStack part : split) {
                try {
                    result.add(Base64.getEncoder().encodeToString(part.serializeAsBytes()));
                } catch (Exception e2) {
                    String msg = "[LOST-AUCTION] FISHCHECK: " + e2.getMessage();
                    plugin.getLogger().severe(msg);
                    System.out.println(msg);
                }
            }
            return result;
        }
    }

    private ItemStack itemFromBase64(String b64) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(b64));
        } catch (Exception e) {
            return null;
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
                List<String> list = new ArrayList<>();
                for (ItemStack item : e.getValue()) {
                    list.addAll(itemToBase64(item));
                }
                data.set("deliveries." + e.getKey().toString(), list);
            }

            data.set("reclaims", null);
            for (Map.Entry<UUID, List<ItemStack>> e : reclaimableItems.entrySet()) {
                List<String> list = new ArrayList<>();
                for (ItemStack item : e.getValue()) {
                    list.addAll(itemToBase64(item));
                }
                data.set("reclaims." + e.getKey().toString(), list);
            }

            List<String> autoClaimList = new ArrayList<>();
            for (UUID uuid : autoClaimPlayers) autoClaimList.add(uuid.toString());
            data.set("auto-claim", autoClaimList);

            data.set("purchases", null);
            for (PurchaseRecord pr : recentPurchases.values()) {
                data.set("purchases." + pr.auctionId, pr.serialize());
            }

            List<String> notifyList = new ArrayList<>();
            for (UUID uuid : newListingNotifications) notifyList.add(uuid.toString());
            data.set("new-listing-notifications", notifyList);

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
        LostAuction.getInstance().logDebug("Auction created: " + id + " by " + getPlayerName(seller) + " (" + item.getType() + " x" + item.getAmount() + ")");
        notifyNewListing(a);
        return a;
    }

    private void notifyNewListing(Auction a) {
        for (UUID uuid : newListingNotifications) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && !uuid.equals(a.seller)) {
                String itemName = a.item.getItemMeta() != null && a.item.getItemMeta().hasDisplayName()
                        ? a.item.getItemMeta().getDisplayName()
                        : formatMaterialName(a.item.getType());
                p.sendMessage("[Auction] New listing: " + itemName + " (" + (a.type == Auction.Type.FIXED ? "$" + a.startingPrice : "Starting at $" + a.startingPrice) + ") by " + getPlayerName(a.seller));
            }
        }
    }

    public boolean toggleNewListingNotifications(UUID player) {
        if (newListingNotifications.contains(player)) {
            newListingNotifications.remove(player);
            save();
            return false;
        } else {
            newListingNotifications.add(player);
            save();
            return true;
        }
    }

    public boolean hasNewListingNotifications(UUID player) {
        return newListingNotifications.contains(player);
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

        // record purchase for undo feature
        boolean undoEnabled = plugin.getConfig().getBoolean("undo-purchase-enabled", true);
        if (undoEnabled) {
            recentPurchases.put(a.id, new PurchaseRecord(a.id, buyer.getUniqueId(), a.seller, a.item, price));
        }

        // record in history for both
        addToHistory(a.seller, a);
        addToHistory(buyer.getUniqueId(), a);

        auctions.remove(a.id);
        save();
        LostAuction.getInstance().logDebug("Purchase: " + buyer.getName() + " bought " + a.id + " from " + getPlayerName(a.seller) + " for " + price);
        return true;
    }

    public PurchaseRecord getPurchaseForUndo(UUID player) {
        long timeout = plugin.getConfig().getLong("undo-purchase-time-minutes", 10) * 60L * 1000L;
        long now = System.currentTimeMillis();
        PurchaseRecord best = null;
        for (PurchaseRecord pr : recentPurchases.values()) {
            if (pr.buyer.equals(player) && now - pr.timestamp < timeout) {
                if (best == null || pr.timestamp > best.timestamp) best = pr;
            }
        }
        return best;
    }

    public boolean processUndoItem(Player buyer, String auctionId, ItemStack returnedItem) {
        if (!plugin.getConfig().getBoolean("undo-purchase-enabled", true)) {
            buyer.sendMessage("[Auction] Undo purchase is disabled on this server.");
            return false;
        }
        PurchaseRecord pr = recentPurchases.get(auctionId);
        if (pr == null || !pr.buyer.equals(buyer.getUniqueId())) {
            buyer.sendMessage("[Auction] No recent purchase found to undo.");
            return false;
        }
        long timeout = plugin.getConfig().getLong("undo-purchase-time-minutes", 10) * 60L * 1000L;
        if (System.currentTimeMillis() - pr.timestamp > timeout) {
            recentPurchases.remove(auctionId);
            buyer.sendMessage("[Auction] The undo window has expired for that purchase.");
            return false;
        }

        if (returnedItem == null || returnedItem.getType().isAir()) {
            buyer.sendMessage("[Auction] You must place the item in the GUI to undo.");
            return false;
        }

        ItemStack originalItem = ItemStack.deserializeBytes(pr.itemBytes);
        if (!returnedItem.isSimilar(originalItem) || returnedItem.getAmount() < originalItem.getAmount()) {
            buyer.sendMessage("[Auction] The item you placed does not match the purchased item.");
            return false;
        }

        if (!econ.has(Bukkit.getOfflinePlayer(pr.seller), pr.price)) {
            buyer.sendMessage("[Auction] The seller no longer has sufficient funds for an undo.");
            return false;
        }

        econ.withdrawPlayer(Bukkit.getOfflinePlayer(pr.seller), pr.price);
        econ.depositPlayer(buyer, pr.price);

        Auction original = new Auction(pr.auctionId, pr.seller, ItemStack.deserializeBytes(pr.itemBytes), Auction.Type.FIXED, pr.price, System.currentTimeMillis() + plugin.getConfig().getLong("default-duration-hours", 24) * 3600L * 1000L);
        String newId;
        Random rand = new Random();
        do { newId = String.format("%04d", rand.nextInt(10000)); } while (auctions.containsKey(newId));
        original.id = newId;
        auctions.put(newId, original);

        recentPurchases.remove(auctionId);
        save();
        LostAuction.getInstance().logDebug("Undo: " + buyer.getName() + " undid purchase " + auctionId + ", re-listed as " + newId);

        Player seller = Bukkit.getPlayer(pr.seller);
        if (seller != null && seller.isOnline()) {
            seller.sendMessage("[Auction] " + buyer.getName() + " undid their purchase of auction " + auctionId + ". The item has been re-listed.");
        }

        buyer.sendMessage("[Auction] Purchase undone. $" + pr.price + " refunded and item re-listed as auction " + newId + ".");
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
            LostAuction.getInstance().logDebug("Auction expired: " + a.id + " (" + a.type + ")");
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

    public List<Auction> searchPlayerHistory(UUID playerId, String searchTerm) {
        List<Auction> all = getPlayerHistory(playerId);
        if (searchTerm == null || searchTerm.isEmpty()) return all;
        String lower = searchTerm.toLowerCase();
        List<Auction> results = new ArrayList<>();
        for (Auction a : all) {
            String itemName = a.item.getType().name().toLowerCase();
            if (a.item.hasItemMeta() && a.item.getItemMeta().hasDisplayName()) {
                itemName = a.item.getItemMeta().getDisplayName().toLowerCase();
            }
            if (itemName.contains(lower)) {
                results.add(a);
            }
        }
        return results;
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

    public static String getPlayerName(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : "Unknown";
    }

    public static String formatMaterialName(org.bukkit.Material material) {
        String[] words = material.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) sb.append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }

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
