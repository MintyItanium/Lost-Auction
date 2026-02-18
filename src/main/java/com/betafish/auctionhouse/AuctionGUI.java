package com.betafish.auctionhouse;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class AuctionGUI implements Listener {
    private final AuctionManager manager;
    private static final Map<Player, ItemStack> pendingSelection = new HashMap<>();

    public AuctionGUI(AuctionManager m) { this.manager = m; }

    private static String formatDuration(long millis) {
        if (millis <= 0) return "Ended";
        long secs = millis / 1000;
        long days = secs / 86400; secs %= 86400;
        long hours = secs / 3600; secs %= 3600;
        long mins = secs / 60; secs %= 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (mins > 0) sb.append(mins).append("m ");
        if (secs > 0 && days==0) sb.append(secs).append("s");
        return sb.toString().trim();
    }

    public static void openMain(Player p, AuctionManager manager) {
        if (!p.hasPermission("beta.auction")) {
            p.sendMessage("You do not have permission to use the auction house.");
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, "Auction House");
        int i = 0;
        for (Auction a : manager.listAuctions()) {
            if (i >= 50) break; // reserve last 4 slots for buttons
            ItemStack item = a.item.clone();
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
            List<String> lore = new ArrayList<>();
            if (a.type == Auction.Type.FIXED) lore.add("Price: " + a.startingPrice);
            else lore.add("Current bid: " + a.currentBid + (a.currentBidder == null ? " (no bids)" : ""));
            long remaining = a.endTime - System.currentTimeMillis();
            lore.add("Ends in: " + formatDuration(remaining));
            meta.setLore(lore);
            String itemName = meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name();
            meta.setDisplayName(ChatColor.GREEN + itemName);
            item.setItemMeta(meta);
            inv.setItem(i++, item);
        }
        // add history button in second-to-last slot
        ItemStack historyBtn = new ItemStack(Material.BOOK);
        ItemMeta historyMeta = historyBtn.getItemMeta();
        historyMeta.setDisplayName(ChatColor.BLUE + "Your Auction History");
        List<String> hLore = new ArrayList<>();
        hLore.add("Click to view your auction history");
        hLore.add("Shows all auctions you've participated in");
        historyMeta.setLore(hLore);
        historyBtn.setItemMeta(historyMeta);
        inv.setItem(52, historyBtn);

        // add search button in third-to-last slot
        ItemStack searchBtn = new ItemStack(Material.COMPASS);
        ItemMeta searchMeta = searchBtn.getItemMeta();
        searchMeta.setDisplayName(ChatColor.GREEN + "Search & Filter");
        List<String> sLore = new ArrayList<>();
        sLore.add("Click to search and filter auctions");
        sLore.add("Find specific items or filter by criteria");
        searchMeta.setLore(sLore);
        searchBtn.setItemMeta(searchMeta);
        inv.setItem(51, searchBtn);

        // add list-item button in last slot
        ItemStack listBtn = new ItemStack(Material.CHEST);
        ItemMeta btnMeta = listBtn.getItemMeta();
        btnMeta.setDisplayName(ChatColor.AQUA + "List Item from Inventory");
        List<String> blore = new ArrayList<>();
        blore.add("Click to choose an item to list for sale or auction");
        btnMeta.setLore(blore);
        listBtn.setItemMeta(btnMeta);
        inv.setItem(53, listBtn);
        p.openInventory(inv);
    }

    public static void openSelectItem(Player p, AuctionManager manager) {
        Inventory inv = Bukkit.createInventory(null, 54, "Select Item to List");
        int i = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || it.getType() == Material.AIR) continue;
            if (i >= 53) break; // reserve last slot for back
            inv.setItem(i++, it.clone());
        }
        // back button
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(ChatColor.RED + "Back to Auction House");
        back.setItemMeta(bm);
        inv.setItem(53, back);
        p.openInventory(inv);
    }

    public static void openChooseCategory(Player p, AuctionManager manager, ItemStack selectedItem) {
        Inventory inv = Bukkit.createInventory(null, 54, "Choose Category");
        Map<String, String> categories = manager.getAllCategories();

        int i = 0;
        for (Map.Entry<String, String> entry : categories.entrySet()) {
            if (i >= 45) break; // reserve some slots for navigation

            String categoryName = entry.getKey();
            String itemType = entry.getValue();

            Material material;
            try {
                material = Material.valueOf(itemType.toUpperCase());
            } catch (IllegalArgumentException e) {
                material = Material.CHEST; // fallback
            }

            ItemStack categoryItem = new ItemStack(material);
            ItemMeta meta = categoryItem.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + categoryName);
            List<String> lore = new ArrayList<>();
            lore.add("Click to select this category");
            lore.add("for your auction listing");
            meta.setLore(lore);
            categoryItem.setItemMeta(meta);
            inv.setItem(i++, categoryItem);
        }

        // Selected item display
        ItemStack displayItem = selectedItem.clone();
        ItemMeta displayMeta = displayItem.getItemMeta();
        if (displayMeta != null) {
            displayMeta.setDisplayName(ChatColor.YELLOW + "Selected Item");
            displayItem.setItemMeta(displayMeta);
        }
        inv.setItem(49, displayItem);

        // Back button
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "Back to Item Selection");
        backBtn.setItemMeta(backMeta);
        inv.setItem(53, backBtn);

        p.openInventory(inv);
    }

    public static void openChooseListingType(Player p, AuctionManager manager, ItemStack selectedItem, String category) {
        Inventory inv = Bukkit.createInventory(null, 9, "Choose Listing Type");
        ItemStack sell = new ItemStack(Material.PAPER);
        ItemMeta sm = sell.getItemMeta();
        sm.setDisplayName(ChatColor.GOLD + "Sell (Fixed Price)");
        List<String> sl = new ArrayList<>();
        sl.add("Click to list this item for a fixed price");
        sl.add("Category: " + category);
        sm.setLore(sl);
        sell.setItemMeta(sm);

        ItemStack auc = new ItemStack(Material.ANVIL);
        ItemMeta am = auc.getItemMeta();
        am.setDisplayName(ChatColor.GREEN + "Auction (Start Bid)");
        List<String> al = new ArrayList<>();
        al.add("Click to start an auction for this item");
        al.add("Category: " + category);
        am.setLore(al);
        auc.setItemMeta(am);

        inv.setItem(3, sell);
        inv.setItem(5, auc);

        // Store the selected item and category for later use
        pendingSelection.put(p, selectedItem);
        // We need to store category too - let's add it to the item lore temporarily
        ItemMeta itemMeta = selectedItem.getItemMeta();
        if (itemMeta != null) {
            List<String> lore = itemMeta.getLore();
            if (lore == null) lore = new ArrayList<>();
            // Remove any existing category marker
            lore.removeIf(line -> line.startsWith("CATEGORY:"));
            lore.add("CATEGORY:" + category);
            itemMeta.setLore(lore);
            selectedItem.setItemMeta(itemMeta);
        }

        p.openInventory(inv);
    }

    public static void openAdmin(Player p, AuctionManager manager) {
        Inventory inv = Bukkit.createInventory(null, 54, "Auction Admin");
        int i = 0;
        for (Auction a : manager.listAuctions()) {
            if (i >= 54) break;
            ItemStack item = a.item.clone();
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
            List<String> lore = new ArrayList<>();
            lore.add("ID: " + a.id);
            lore.add("Seller: " + a.seller.toString());
            lore.add("Type: " + a.type.name());
            if (a.type == Auction.Type.FIXED) lore.add("Price: " + a.startingPrice);
            else lore.add("Current bid: " + a.currentBid + (a.currentBidder == null ? " (no bids)" : ""));
            long remaining = a.endTime - System.currentTimeMillis();
            lore.add("Ends in: " + formatDuration(remaining));
            lore.add("");
            lore.add("Left-click to force-end this auction.");
            meta.setLore(lore);
            meta.setDisplayName(ChatColor.RED + "[ADMIN] Auction: " + a.id);
            item.setItemMeta(meta);
            inv.setItem(i++, item);
        }
        p.openInventory(inv);
    }

    public static void openSearch(Player p, AuctionManager manager) {
        Inventory inv = Bukkit.createInventory(null, 54, "Search & Filter Auctions");

        // Search by name button
        ItemStack searchNameBtn = new ItemStack(Material.NAME_TAG);
        ItemMeta searchNameMeta = searchNameBtn.getItemMeta();
        searchNameMeta.setDisplayName(ChatColor.YELLOW + "Search by Item Name");
        List<String> snLore = new ArrayList<>();
        snLore.add("Click to search auctions by item name");
        snLore.add("Find specific items you're looking for");
        searchNameMeta.setLore(snLore);
        searchNameBtn.setItemMeta(searchNameMeta);
        inv.setItem(10, searchNameBtn);

        // Filter by price range button
        ItemStack priceFilterBtn = new ItemStack(Material.GOLD_INGOT);
        ItemMeta priceFilterMeta = priceFilterBtn.getItemMeta();
        priceFilterMeta.setDisplayName(ChatColor.GOLD + "Filter by Price Range");
        List<String> pfLore = new ArrayList<>();
        pfLore.add("Click to filter auctions by price");
        pfLore.add("Set minimum and maximum prices");
        priceFilterMeta.setLore(pfLore);
        priceFilterBtn.setItemMeta(priceFilterMeta);
        inv.setItem(12, priceFilterBtn);

        // Filter by category button
        ItemStack categoryFilterBtn = new ItemStack(Material.BOOKSHELF);
        ItemMeta categoryFilterMeta = categoryFilterBtn.getItemMeta();
        categoryFilterMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Filter by Category");
        List<String> cfLore = new ArrayList<>();
        cfLore.add("Click to filter auctions by category");
        categoryFilterMeta.setLore(cfLore);
        categoryFilterBtn.setItemMeta(categoryFilterMeta);
        inv.setItem(16, categoryFilterBtn);

        // Show all button
        ItemStack showAllBtn = new ItemStack(Material.BOOKSHELF);
        ItemMeta showAllMeta = showAllBtn.getItemMeta();
        showAllMeta.setDisplayName(ChatColor.GREEN + "Show All Auctions");
        List<String> saLore = new ArrayList<>();
        saLore.add("Click to return to the main auction view");
        showAllMeta.setLore(saLore);
        showAllBtn.setItemMeta(showAllMeta);
        inv.setItem(31, showAllBtn);

        // Back button
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "Back to Auction House");
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

        p.openInventory(inv);
    }

    public static void openSearchResults(Player p, AuctionManager manager, String searchTerm) {
        Inventory inv = Bukkit.createInventory(null, 54, "Search Results: '" + searchTerm + "'");
        int i = 0;
        for (Auction a : manager.listAuctions()) {
            if (i >= 50) break;

            // Check if item name contains search term
            String itemName = a.item.getType().name().toLowerCase();
            if (a.item.hasItemMeta() && a.item.getItemMeta().hasDisplayName()) {
                itemName = a.item.getItemMeta().getDisplayName().toLowerCase();
            }

            if (!itemName.contains(searchTerm)) continue;

            ItemStack item = a.item.clone();
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
            List<String> lore = new ArrayList<>();
            if (a.type == Auction.Type.FIXED) lore.add("Price: " + a.startingPrice);
            else lore.add("Current bid: " + a.currentBid + (a.currentBidder == null ? " (no bids)" : ""));
            long remaining = a.endTime - System.currentTimeMillis();
            lore.add("Ends in: " + formatDuration(remaining));
            meta.setLore(lore);
            String displayName = meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name();
            meta.setDisplayName(ChatColor.GREEN + displayName);
            item.setItemMeta(meta);
            inv.setItem(i++, item);
        }

        // Back button
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "Back to Search");
        backBtn.setItemMeta(backMeta);
        inv.setItem(53, backBtn);

        p.openInventory(inv);
    }

    public static void openCategoryFilter(Player p, AuctionManager manager) {
        Inventory inv = Bukkit.createInventory(null, 54, "Filter by Category");
        Map<String, String> categories = manager.getAllCategories();

        int i = 0;
        for (Map.Entry<String, String> entry : categories.entrySet()) {
            if (i >= 45) break; // reserve some slots for navigation

            String categoryName = entry.getKey();
            String itemType = entry.getValue();

            Material material;
            try {
                material = Material.valueOf(itemType.toUpperCase());
            } catch (IllegalArgumentException e) {
                material = Material.CHEST; // fallback
            }

            ItemStack categoryItem = new ItemStack(material);
            ItemMeta meta = categoryItem.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + categoryName);
            List<String> lore = new ArrayList<>();
            lore.add("Click to show auctions in this category");
            meta.setLore(lore);
            categoryItem.setItemMeta(meta);
            inv.setItem(i++, categoryItem);
        }

        // Back button
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "Back");
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

        p.openInventory(inv);
    }

    public static void openHistory(Player p, AuctionManager manager) {
        if (!p.hasPermission("beta.auction")) {
            p.sendMessage("You do not have permission to use the auction house.");
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, "Your Auction History");
        List<Auction> history = manager.getPlayerHistory(p.getUniqueId());
        int i = 0;
        for (Auction a : history) {
            if (i >= 54) break;
            ItemStack item = a.item.clone();
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
            List<String> lore = new ArrayList<>();
            lore.add("ID: " + a.id);
            lore.add("Type: " + (a.type == Auction.Type.FIXED ? "Fixed Price" : "Auction"));

            // Check if auction is still active
            boolean isActive = manager.getAuction(a.id) != null;
            if (isActive) {
                lore.add("Status: Active");
                lore.add("Current Price: $" + (a.currentBid > 0 ? a.currentBid : a.startingPrice));
                long remaining = a.endTime - System.currentTimeMillis();
                lore.add("Time Remaining: " + formatDuration(remaining));
            } else {
                lore.add("Final Price: $" + (a.currentBid > 0 ? a.currentBid : a.startingPrice));
                String status = a.currentBidder != null ? "Sold/Won" : "Expired/No bids";
                lore.add("Status: " + status);
            }

            String role = a.seller.equals(p.getUniqueId()) ? "Seller" : "Bidder";
            lore.add("Your Role: " + role);
            meta.setLore(lore);
            String itemName = meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name();
            ChatColor color = isActive ? ChatColor.GREEN : ChatColor.YELLOW;
            meta.setDisplayName(color + itemName);
            item.setItemMeta(meta);
            inv.setItem(i++, item);
        }
        p.openInventory(inv);
    }

    public static void openAllHistory(Player p, AuctionManager manager) {
        Inventory inv = Bukkit.createInventory(null, 54, "All Auction History");
        List<Auction> history = manager.getAllHistory();
        int i = 0;
        for (Auction a : history) {
            if (i >= 54) break;
            ItemStack item = a.item.clone();
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
            List<String> lore = new ArrayList<>();
            lore.add("ID: " + a.id);
            lore.add("Seller: " + a.seller.toString());
            if (a.currentBidder != null) {
                lore.add("Winner: " + a.currentBidder.toString());
            }
            lore.add("Type: " + (a.type == Auction.Type.FIXED ? "Fixed Price" : "Auction"));

            // Check if auction is still active
            boolean isActive = manager.getAuction(a.id) != null;
            if (isActive) {
                lore.add("Status: Active");
                lore.add("Current Price: $" + (a.currentBid > 0 ? a.currentBid : a.startingPrice));
                long remaining = a.endTime - System.currentTimeMillis();
                lore.add("Time Remaining: " + formatDuration(remaining));
            } else {
                lore.add("Final Price: $" + (a.currentBid > 0 ? a.currentBid : a.startingPrice));
                String status = a.currentBidder != null ? "Sold" : "Expired";
                lore.add("Status: " + status);
            }

            meta.setLore(lore);
            String itemName = meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name();
            ChatColor color = isActive ? ChatColor.GREEN : ChatColor.GOLD;
            meta.setDisplayName(color + itemName);
            item.setItemMeta(meta);
            inv.setItem(i++, item);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClickSearch(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("Search & Filter Auctions")) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (!p.hasPermission("beta.auction")) { p.sendMessage("You do not have permission to use the auction house. :|"); p.closeInventory(); return; }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta != null && clickedMeta.hasDisplayName()) {
            String displayName = clickedMeta.getDisplayName();
            if (displayName.equals(ChatColor.YELLOW + "Search by Item Name")) {
                AnvilListener.openAnvilForSearch(p, manager);
            } else if (displayName.equals(ChatColor.GOLD + "Filter by Price Range")) {
                // I don't really know to to get price filtering to work yet, but I hope to add it soon.
                p.sendMessage("[Auction] Price filtering isn't here yet, but might be soon. -Minty");
                p.closeInventory();
            } else if (displayName.equals(ChatColor.BLUE + "Filter by Auction Type")) {
                openTypeFilter(p, manager);
            } else if (displayName.equals(ChatColor.LIGHT_PURPLE + "Filter by Category")) {
                openCategoryFilter(p, manager);
            } else if (displayName.equals(ChatColor.GREEN + "Show All Auctions")) {
                openMain(p, manager);
            } else if (displayName.equals(ChatColor.RED + "Back to Auction House")) {
                openMain(p, manager);
            }
        }
    }

    public static void openTypeFilter(Player p, AuctionManager manager) {
        Inventory inv = Bukkit.createInventory(null, 27, "Filter by Auction Type");

        // Fixed Price button
        ItemStack fixedBtn = new ItemStack(Material.PAPER);
        ItemMeta fixedMeta = fixedBtn.getItemMeta();
        fixedMeta.setDisplayName(ChatColor.GOLD + "Show Fixed Price Only");
        List<String> fLore = new ArrayList<>();
        fLore.add("Buy-it-now auctions");
        fixedMeta.setLore(fLore);
        fixedBtn.setItemMeta(fixedMeta);
        inv.setItem(11, fixedBtn);

        // Auction button
        ItemStack auctionBtn = new ItemStack(Material.ANVIL);
        ItemMeta auctionMeta = auctionBtn.getItemMeta();
        auctionMeta.setDisplayName(ChatColor.GREEN + "Show Auctions Only");
        List<String> aLore = new ArrayList<>();
        aLore.add("Click to show only bid-based auctions");
        auctionMeta.setLore(aLore);
        auctionBtn.setItemMeta(auctionMeta);
        inv.setItem(15, auctionBtn);

        // Back button
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "Back");
        backBtn.setItemMeta(backMeta);
        inv.setItem(22, backBtn);

        p.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClickTypeFilter(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("Filter by Auction Type")) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta != null && clickedMeta.hasDisplayName()) {
            String displayName = clickedMeta.getDisplayName();
            if (displayName.equals(ChatColor.GOLD + "Show Fixed Price Only")) {
                openFilteredAuctions(p, manager, Auction.Type.FIXED);
            } else if (displayName.equals(ChatColor.GREEN + "Show Auctions Only")) {
                openFilteredAuctions(p, manager, Auction.Type.AUCTION);
            } else if (displayName.equals(ChatColor.RED + "Back")) {
                openSearch(p, manager);
            }
        }
    }

    public static void openFilteredAuctions(Player p, AuctionManager manager, Auction.Type filterType) {
        Inventory inv = Bukkit.createInventory(null, 54, "Filtered Auctions: " + filterType.name());
        int i = 0;
        for (Auction a : manager.listAuctions()) {
            if (a.type != filterType) continue;
            if (i >= 50) break;

            ItemStack item = a.item.clone();
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
            List<String> lore = new ArrayList<>();
            if (filterType == Auction.Type.FIXED) lore.add("Price: " + a.startingPrice);
            else lore.add("Current bid: " + a.currentBid + (a.currentBidder == null ? " (no bids)" : ""));
            long remaining = a.endTime - System.currentTimeMillis();
            lore.add("Ends in: " + formatDuration(remaining));
            meta.setLore(lore);
            String itemName = meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name();
            meta.setDisplayName(ChatColor.GREEN + itemName);
            item.setItemMeta(meta);
            inv.setItem(i++, item);
        }

        // Back button
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "Back to Search");
        backBtn.setItemMeta(backMeta);
        inv.setItem(53, backBtn);

        p.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClickFiltered(InventoryClickEvent e) {
        if (!e.getView().getTitle().startsWith("Filtered Auctions:")) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta != null && clickedMeta.hasDisplayName() && clickedMeta.getDisplayName().equals(ChatColor.RED + "Back to Search")) {
            openSearch(p, manager);
            return;
        }

        // Handle auction clicks (similar to main GUI but with different slot limits)
        int slot = e.getSlot();
        if (slot < 0 || slot >= 50) return; // 50-53 are buttons/reserved

        List<Auction> auctionList = new ArrayList<>();
        String title = e.getView().getTitle();
        Auction.Type filterType = title.contains("FIXED") ? Auction.Type.FIXED : Auction.Type.AUCTION;

        for (Auction a : manager.listAuctions()) {
            if (a.type == filterType) auctionList.add(a);
        }

        if (slot >= auctionList.size()) return;
        Auction a = auctionList.get(slot);

        if (a.type == Auction.Type.FIXED) {
            double price = a.startingPrice;
            if (!manager.getEconomy().has(p, price)) { p.sendMessage("You cannot afford this item."); return; }
            manager.getEconomy().withdrawPlayer(p, price);
            manager.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(a.seller), price);
            // deliver item
            if (p.isOnline()) p.getInventory().addItem(a.item);
            manager.removeAuction(a.id);
            p.sendMessage("[Auction] You bought item for " + price);
        } else {
            // open anvil to input bid
            AnvilListener.openAnvilForBid(p, a, manager);
        }
    }

    @EventHandler
    public void onInventoryClickSearchResults(InventoryClickEvent e) {
        if (!e.getView().getTitle().startsWith("Search Results:")) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta != null && clickedMeta.hasDisplayName() && clickedMeta.getDisplayName().equals(ChatColor.RED + "Back to Search")) {
            openSearch(p, manager);
            return;
        }

        // Handle auction clicks (similar to main GUI)
        int slot = e.getSlot();
        if (slot < 0 || slot >= 50) return; // 50-53 are buttons/reserved

        String title = e.getView().getTitle();
        String searchTerm = title.substring(title.indexOf("'") + 1, title.lastIndexOf("'")).toLowerCase();

        List<Auction> searchResults = new ArrayList<>();
        for (Auction a : manager.listAuctions()) {
            String itemName = a.item.getType().name().toLowerCase();
            if (a.item.hasItemMeta() && a.item.getItemMeta().hasDisplayName()) {
                itemName = a.item.getItemMeta().getDisplayName().toLowerCase();
            }
            if (itemName.contains(searchTerm)) {
                searchResults.add(a);
            }
        }

        if (slot >= searchResults.size()) return;
        Auction a = searchResults.get(slot);

        if (a.type == Auction.Type.FIXED) {
            double price = a.startingPrice;
            if (!manager.getEconomy().has(p, price)) { p.sendMessage("You cannot afford this item."); return; }
            manager.getEconomy().withdrawPlayer(p, price);
            manager.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(a.seller), price);
            // deliver item
            if (p.isOnline()) p.getInventory().addItem(a.item);
            manager.removeAuction(a.id);
            p.sendMessage("[Auction] You bought item for " + price);
        } else {
            // open anvil to input bid
            AnvilListener.openAnvilForBid(p, a, manager);
        }
    }

    @EventHandler
    public void onInventoryClickCategoryFilter(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("Filter by Category")) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta != null && clickedMeta.hasDisplayName()) {
            String displayName = clickedMeta.getDisplayName();
            if (displayName.equals(ChatColor.RED + "Back")) {
                openSearch(p, manager);
                return;
            } else if (displayName.startsWith(ChatColor.GREEN.toString())) {
                // Category selected
                String category = displayName.substring(ChatColor.GREEN.toString().length());
                openFilteredCategoryAuctions(p, manager, category);
                return;
            }
        }
    }

    public static void openFilteredCategoryAuctions(Player p, AuctionManager manager, String category) {
        Inventory inv = Bukkit.createInventory(null, 54, "Category: " + category);
        int i = 0;
        for (Auction a : manager.listAuctions()) {
            if (!category.equals(a.category)) continue;
            if (i >= 50) break;

            ItemStack item = a.item.clone();
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
            List<String> lore = new ArrayList<>();
            if (a.type == Auction.Type.FIXED) lore.add("Price: " + a.startingPrice);
            else lore.add("Current bid: " + a.currentBid + (a.currentBidder == null ? " (no bids)" : ""));
            long remaining = a.endTime - System.currentTimeMillis();
            lore.add("Ends in: " + formatDuration(remaining));
            lore.add("Category: " + a.category);
            meta.setLore(lore);
            String itemName = meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name();
            meta.setDisplayName(ChatColor.GREEN + itemName);
            item.setItemMeta(meta);
            inv.setItem(i++, item);
        }

        // Back button
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "Back to Search");
        backBtn.setItemMeta(backMeta);
        inv.setItem(53, backBtn);

        p.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClickFilteredCategory(InventoryClickEvent e) {
        if (!e.getView().getTitle().startsWith("Category:")) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta != null && clickedMeta.hasDisplayName() && clickedMeta.getDisplayName().equals(ChatColor.RED + "Back to Search")) {
            openSearch(p, manager);
            return;
        }

        // Handle auction clicks (similar to main GUI)
        int slot = e.getSlot();
        if (slot < 0 || slot >= 50) return; // 50-53 are buttons/reserved

        String title = e.getView().getTitle();
        String category = title.substring(title.indexOf(": ") + 2);

        List<Auction> categoryResults = new ArrayList<>();
        for (Auction a : manager.listAuctions()) {
            if (category.equals(a.category)) {
                categoryResults.add(a);
            }
        }

        if (slot >= categoryResults.size()) return;
        Auction a = categoryResults.get(slot);

        if (a.type == Auction.Type.FIXED) {
            double price = a.startingPrice;
            if (!manager.getEconomy().has(p, price)) { p.sendMessage("You cannot afford this item."); return; }
            manager.getEconomy().withdrawPlayer(p, price);
            manager.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(a.seller), price);
            // deliver item
            if (p.isOnline()) p.getInventory().addItem(a.item);
            manager.removeAuction(a.id);
            p.sendMessage("[Auction] You bought item for " + price);
        } else {
            // open anvil to input bid
            AnvilListener.openAnvilForBid(p, a, manager);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("Auction House")) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (!p.hasPermission("beta.auction")) { p.sendMessage("You do not have permission to use the auction house."); p.closeInventory(); return; }
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta != null && clickedMeta.hasDisplayName()) {
            if (clickedMeta.getDisplayName().equals(ChatColor.BLUE + "Your Auction History")) {
                openHistory(p, manager);
                return;
            } else if (clickedMeta.getDisplayName().equals(ChatColor.GREEN + "Search & Filter")) {
                openSearch(p, manager);
                return;
            } else if (clickedMeta.getDisplayName().equals(ChatColor.AQUA + "List Item from Inventory")) {
                openSelectItem(p, manager);
                return;
            }
        }
        int slot = e.getSlot();
        if (slot < 0 || slot >= 50) return; // 50-53 are buttons
        List<Auction> auctionList = new ArrayList<>(manager.listAuctions());
        if (slot >= auctionList.size()) return;
        Auction a = auctionList.get(slot);

        if (a.type == Auction.Type.FIXED) {
            double price = a.startingPrice;
            if (!manager.getEconomy().has(p, price)) { p.sendMessage("You cannot afford this item."); return; }
            manager.getEconomy().withdrawPlayer(p, price);
            manager.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(a.seller), price);
            // deliver item
            if (p.isOnline()) p.getInventory().addItem(a.item);
            manager.removeAuction(a.id);
            p.sendMessage("[Auction] You bought item for " + price);
        } else {
            // open anvil to input bid
            AnvilListener.openAnvilForBid(p, a, manager);
        }
    }

    @EventHandler
    public void onInventoryClickCategory(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("Choose Category")) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta != null && clickedMeta.hasDisplayName()) {
            String displayName = clickedMeta.getDisplayName();
            if (displayName.equals(ChatColor.RED + "Back to Item Selection")) {
                // Return to item selection - but we need to return the item to inventory
                ItemStack selectedItem = pendingSelection.get(p);
                if (selectedItem != null) {
                    p.getInventory().addItem(selectedItem);
                    pendingSelection.remove(p);
                }
                openSelectItem(p, manager);
                return;
            } else if (displayName.startsWith(ChatColor.GREEN.toString())) {
                // Category selected
                String category = displayName.substring(ChatColor.GREEN.toString().length());
                ItemStack selectedItem = pendingSelection.get(p);
                if (selectedItem == null) {
                    p.sendMessage("No item selected.");
                    return;
                }
                openChooseListingType(p, manager, selectedItem, category);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClickSelect(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("Select Item to List") && !e.getView().getTitle().equals("Choose Listing Type")) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (!p.hasPermission("beta.auction")) { p.sendMessage("You do not have permission to use the auction house."); p.closeInventory(); return; }

        if (e.getView().getTitle().equals("Select Item to List")) {
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            if (clicked.getType() == Material.BARRIER) { openMain(p, manager); return; }

            // remove one matching stack from player's inventory and store selection
            ItemStack sel = clicked.clone();
            // try to remove the exact stack (amount included)
            p.getInventory().removeItem(sel);
            pendingSelection.put(p, sel);
            openChooseCategory(p, manager, sel);
            return;
        }

        if (e.getView().getTitle().equals("Choose Listing Type")) {
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            ItemStack sel = pendingSelection.remove(p);
            if (sel == null) { p.sendMessage("No item selected to list."); p.closeInventory(); return; }

            // Extract category from item lore
            String category = "General"; // default
            ItemMeta itemMeta = sel.getItemMeta();
            if (itemMeta != null && itemMeta.getLore() != null) {
                for (String line : itemMeta.getLore()) {
                    if (line.startsWith("CATEGORY:")) {
                        category = line.substring("CATEGORY:".length());
                        break;
                    }
                }
                // Remove the category marker from lore
                List<String> lore = itemMeta.getLore();
                lore.removeIf(line -> line.startsWith("CATEGORY:"));
                itemMeta.setLore(lore);
                sel.setItemMeta(itemMeta);
            }

            if (clicked.getType() == Material.PAPER) {
                AnvilListener.openAnvilForListing(p, sel, manager, Auction.Type.FIXED, category);
            } else if (clicked.getType() == Material.ANVIL) {
                AnvilListener.openAnvilForListing(p, sel, manager, Auction.Type.AUCTION, category);
            } else {
                // unknown, return item
                p.getInventory().addItem(sel);
                p.sendMessage("Cancelled listing.");
            }
            p.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClickAdmin(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("Auction Admin")) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (!p.hasPermission("beta.auction.admin")) { p.sendMessage("You do not have permission to use the auction admin GUI."); p.closeInventory(); return; }
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        List<String> lore = clicked.getItemMeta() == null ? null : clicked.getItemMeta().getLore();
        if (lore == null || lore.isEmpty()) return;
        String idLine = lore.get(0);
        String id = idLine.replace("ID: ", "");
        boolean ok = manager.forceEnd(id);
        if (ok) p.sendMessage("[AuctionAdmin] Auction " + id + " was force-ended.");
        else p.sendMessage("[AuctionAdmin] Auction " + id + " not found.");
        p.closeInventory();
    }

    @EventHandler
    public void onInventoryClickHistory(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("Your Auction History") && !e.getView().getTitle().equals("All Auction History")) return;
        e.setCancelled(true);
        // History GUIs are read-only, just prevent interaction
    }
}
