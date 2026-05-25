package com.betafish.auctionhouse;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
    private static final Map<Player, String> pendingCategory = new HashMap<>();
    private static final Map<Player, Auction.Type> pendingListingType = new HashMap<>();
    private static final Map<Player, Long> pendingPrice = new HashMap<>();

    public AuctionGUI(AuctionManager m) { this.manager = m; }

    private static String formatMaterialName(Material material) {
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
        if (!p.hasPermission("lost.auction")) {
            p.sendMessage("You do not have permission to use the auction house.");
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 27, "Auction House");

        // Browse All Auctions button
        ItemStack browseBtn = new ItemStack(Material.CHEST);
        ItemMeta browseMeta = browseBtn.getItemMeta();
        browseMeta.setDisplayName(ChatColor.GREEN + "Browse All Auctions");
        List<String> bLore = new ArrayList<>();
        bLore.add("Click to browse all available auctions");
        bLore.add("View and bid on items for sale");
        browseMeta.setLore(bLore);
        browseBtn.setItemMeta(browseMeta);
        inv.setItem(10, browseBtn);

        // Browse Categories button
        ItemStack catBtn = new ItemStack(Material.BOOKSHELF);
        ItemMeta catMeta = catBtn.getItemMeta();
        catMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Browse Categories");
        List<String> cLore = new ArrayList<>();
        cLore.add("Click to browse auctions by category");
        catMeta.setLore(cLore);
        catBtn.setItemMeta(catMeta);
        inv.setItem(12, catBtn);

        // Search button
        ItemStack searchBtn = new ItemStack(Material.COMPASS);
        ItemMeta searchMeta = searchBtn.getItemMeta();
        searchMeta.setDisplayName(ChatColor.YELLOW + "Search & Filter");
        List<String> sLore = new ArrayList<>();
        sLore.add("Click to search and filter auctions");
        sLore.add("Find specific items or filter by criteria");
        searchMeta.setLore(sLore);
        searchBtn.setItemMeta(searchMeta);
        inv.setItem(14, searchBtn);

        // History button
        ItemStack historyBtn = new ItemStack(Material.BOOK);
        ItemMeta historyMeta = historyBtn.getItemMeta();
        historyMeta.setDisplayName(ChatColor.BLUE + "Your Auction History");
        List<String> hLore = new ArrayList<>();
        hLore.add("Click to view your auction history");
        hLore.add("Shows all auctions you've participated in");
        historyMeta.setLore(hLore);
        historyBtn.setItemMeta(historyMeta);
        inv.setItem(16, historyBtn);

        // List-item button
        ItemStack listBtn = new ItemStack(Material.ANVIL);
        ItemMeta btnMeta = listBtn.getItemMeta();
        btnMeta.setDisplayName(ChatColor.AQUA + "List Item for Sale");
        List<String> blore = new ArrayList<>();
        blore.add("Click to choose an item to list for sale or auction");
        btnMeta.setLore(blore);
        listBtn.setItemMeta(btnMeta);
        inv.setItem(22, listBtn);

        // My listings button
        ItemStack myListingsBtn = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta myListingsMeta = myListingsBtn.getItemMeta();
        myListingsMeta.setDisplayName(ChatColor.GOLD + "My Listings");
        List<String> mlLore = new ArrayList<>();
        mlLore.add("View and cancel your active listings");
        myListingsMeta.setLore(mlLore);
        myListingsBtn.setItemMeta(myListingsMeta);
        inv.setItem(4, myListingsBtn);

        p.openInventory(inv);
    }

    public static void openBrowseCategories(Player p, AuctionManager manager, int page) {
        if (!p.hasPermission("lost.auction")) {
            p.sendMessage("You do not have permission to use the auction house.");
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, "Browse Categories - Page " + (page + 1));

        // Border fill
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(" ");
        border.setItemMeta(borderMeta);
        for (int i = 0; i <= 8; i++) inv.setItem(i, border.clone());
        for (int i = 45; i <= 53; i++) inv.setItem(i, border.clone());
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9, border.clone());
            inv.setItem(row * 9 + 8, border.clone());
        }

        // Gold ingot — show balance (top row center, slot 4)
        double balance = manager.getEconomy().getBalance(p);
        ItemStack balanceBtn = new ItemStack(Material.GOLD_INGOT);
        ItemMeta balanceMeta = balanceBtn.getItemMeta();
        balanceMeta.setDisplayName(ChatColor.GOLD + "Your Balance: $" + String.format("%.2f", balance));
        balanceBtn.setItemMeta(balanceMeta);
        inv.setItem(4, balanceBtn);

        // Back barrier (bottom row center, slot 49)
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "Back to Auction House");
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

        // Category icons in the interior (rows 1-4, cols 1-7 = 28 slots per page)
        int[] interiorSlots = new int[28];
        int idx = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                interiorSlots[idx++] = row * 9 + col;
            }
        }

        List<Map.Entry<String, String>> categoryList = new ArrayList<>(manager.getAllCategories().entrySet());
        int itemsPerPage = 28;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, categoryList.size());

        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<String, String> entry = categoryList.get(i);
            String categoryName = entry.getKey();
            Material material;
            try {
                material = Material.valueOf(entry.getValue().toUpperCase());
            } catch (IllegalArgumentException ex) {
                material = Material.CHEST;
            }
            ItemStack categoryItem = new ItemStack(material);
            ItemMeta meta = categoryItem.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + categoryName);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to browse auctions in this category");
            meta.setLore(lore);
            categoryItem.setItemMeta(meta);
            inv.setItem(interiorSlots[i - startIndex], categoryItem);
        }

        // Previous page button (bottom-left corner, slot 45)
        if (page > 0) {
            ItemStack prevBtn = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevBtn.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "Previous Page");
            prevBtn.setItemMeta(prevMeta);
            inv.setItem(45, prevBtn);
        }

        // Next page button (bottom-right corner, slot 53)
        if (endIndex < categoryList.size()) {
            ItemStack nextBtn = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextBtn.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page");
            nextBtn.setItemMeta(nextMeta);
            inv.setItem(53, nextBtn);
        }

        p.openInventory(inv);
    }

    private static ItemStack makeBorder() {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = border.getItemMeta();
        m.setDisplayName(" ");
        border.setItemMeta(m);
        return border;
    }

    private static void fillBorder(Inventory inv) {
        ItemStack b = makeBorder();
        for (int i = 0; i <= 8; i++) inv.setItem(i, b.clone());
        for (int i = 45; i <= 53; i++) inv.setItem(i, b.clone());
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9, b.clone());
            inv.setItem(row * 9 + 8, b.clone());
        }
    }

    private static void setBalanceItem(Inventory inv, Player p, AuctionManager manager, int slot) {
        double balance = manager.getEconomy().getBalance(p);
        ItemStack gold = new ItemStack(Material.GOLD_INGOT);
        ItemMeta m = gold.getItemMeta();
        m.setDisplayName(ChatColor.GOLD + "Your Balance: $" + String.format("%.2f", balance));
        gold.setItemMeta(m);
        inv.setItem(slot, gold);
    }

    public static void openBrowseAuctions(Player p, AuctionManager manager, int page) {
        if (!p.hasPermission("lost.auction")) {
            p.sendMessage("You do not have permission to use the auction house.");
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, "Browse Auctions - Page " + (page + 1));
        fillBorder(inv);
        setBalanceItem(inv, p, manager, 4);

        List<Auction> auctions = new ArrayList<>(manager.listAuctions());
        int itemsPerPage = 28;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, auctions.size());

        for (int i = startIndex; i < endIndex; i++) {
            int idx = i - startIndex;
            int row = idx / 7 + 1;
            int col = idx % 7 + 1;
            Auction a = auctions.get(i);
            ItemStack item = a.item.clone();
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
            List<String> lore = new ArrayList<>();
            if (a.type == Auction.Type.FIXED) lore.add("Price: " + a.startingPrice);
            else lore.add("Current bid: " + a.currentBid + (a.currentBidder == null ? " (no bids)" : ""));
            long remaining = a.endTime - System.currentTimeMillis();
            lore.add("Ends in: " + formatDuration(remaining));
            meta.setLore(lore);
            String itemName = meta.hasDisplayName() ? meta.getDisplayName() : formatMaterialName(item.getType());
            meta.setDisplayName(ChatColor.GREEN + itemName);
            item.setItemMeta(meta);
            inv.setItem(row * 9 + col, item);
        }

        if (page > 0) {
            ItemStack prevBtn = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevBtn.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "Previous Page");
            prevBtn.setItemMeta(prevMeta);
            inv.setItem(45, prevBtn);
        }
        if (endIndex < auctions.size()) {
            ItemStack nextBtn = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextBtn.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page");
            nextBtn.setItemMeta(nextMeta);
            inv.setItem(53, nextBtn);
        }
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "Back to Menu");
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

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
            if (i >= 45) break; // reserve slots for the nav bar and to keep it looking clean.

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
        fillBorder(inv);
        setBalanceItem(inv, p, manager, 4);

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

    public static void openSearchResults(Player p, AuctionManager manager, String searchTerm, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, "Search Results: '" + searchTerm + "' - Page " + (page + 1));
        List<Auction> searchResults = new ArrayList<>();
        for (Auction a : manager.listAuctions()) {
            String itemName = a.item.getType().name().toLowerCase();
            if (a.item.hasItemMeta() && a.item.getItemMeta().hasDisplayName()) {
                itemName = a.item.getItemMeta().getDisplayName().toLowerCase();
            }
            if (itemName.contains(searchTerm.toLowerCase())) {
                searchResults.add(a);
            }
        }
        int itemsPerPage = 45;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, searchResults.size());
        
        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            Auction a = searchResults.get(i);
            ItemStack item = a.item.clone();
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
            List<String> lore = new ArrayList<>();
            if (a.type == Auction.Type.FIXED) lore.add("Price: " + a.startingPrice);
            else lore.add("Current bid: " + a.currentBid + (a.currentBidder == null ? " (no bids)" : ""));
            long remaining = a.endTime - System.currentTimeMillis();
            lore.add("Ends in: " + formatDuration(remaining));
            meta.setLore(lore);
            String displayName = meta.hasDisplayName() ? meta.getDisplayName() : formatMaterialName(item.getType());
            meta.setDisplayName(ChatColor.GREEN + displayName);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }

        // Previous page button
        if (page > 0) {
            ItemStack prevBtn = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevBtn.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "Previous Page");
            prevBtn.setItemMeta(prevMeta);
            inv.setItem(48, prevBtn);
        }
        
        // Next page button
        if (endIndex < searchResults.size()) {
            ItemStack nextBtn = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextBtn.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page");
            nextBtn.setItemMeta(nextMeta);
            inv.setItem(50, nextBtn);
        }
        
        // Back button
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "Back to Search");
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

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

    public static void openHistory(Player p, AuctionManager manager, int page) {
        if (!p.hasPermission("lost.auction")) {
            p.sendMessage("You do not have permission to use the auction house.");
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, "Your Auction History - Page " + (page + 1));
        fillBorder(inv);
        setBalanceItem(inv, p, manager, 4);

        List<Auction> history = manager.getPlayerHistory(p.getUniqueId());
        int itemsPerPage = 28;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, history.size());

        for (int i = startIndex; i < endIndex; i++) {
            int idx = i - startIndex;
            int row = idx / 7 + 1;
            int col = idx % 7 + 1;
            Auction a = history.get(i);
            ItemStack item = a.item.clone();
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
            List<String> lore = new ArrayList<>();
            lore.add("ID: " + a.id);
            lore.add("Type: " + (a.type == Auction.Type.FIXED ? "Fixed Price" : "Auction"));

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
            String itemName = meta.hasDisplayName() ? meta.getDisplayName() : formatMaterialName(item.getType());
            ChatColor color = isActive ? ChatColor.GREEN : ChatColor.YELLOW;
            meta.setDisplayName(color + itemName);
            item.setItemMeta(meta);
            inv.setItem(row * 9 + col, item);
        }

        if (page > 0) {
            ItemStack prevBtn = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevBtn.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "Previous Page");
            prevBtn.setItemMeta(prevMeta);
            inv.setItem(45, prevBtn);
        }
        if (endIndex < history.size()) {
            ItemStack nextBtn = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextBtn.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page");
            nextBtn.setItemMeta(nextMeta);
            inv.setItem(53, nextBtn);
        }

        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "Back to Auction House");
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

        p.openInventory(inv);
    }

    public static void openAllHistory(Player p, AuctionManager manager, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, "All Auction History - Page " + (page + 1));
        fillBorder(inv);

        List<Auction> history = manager.getAllHistory();
        int itemsPerPage = 28;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, history.size());

        for (int i = startIndex; i < endIndex; i++) {
            int idx = i - startIndex;
            int row = idx / 7 + 1;
            int col = idx % 7 + 1;
            Auction a = history.get(i);
            ItemStack item = a.item.clone();
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
            List<String> lore = new ArrayList<>();
            lore.add("ID: " + a.id);
            lore.add("Seller: " + a.seller.toString());
            if (a.currentBidder != null) {
                lore.add("Winner: " + a.currentBidder.toString());
            }
            lore.add("Type: " + (a.type == Auction.Type.FIXED ? "Fixed Price" : "Auction"));

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
            String itemName = meta.hasDisplayName() ? meta.getDisplayName() : formatMaterialName(item.getType());
            ChatColor color = isActive ? ChatColor.GREEN : ChatColor.GOLD;
            meta.setDisplayName(color + itemName);
            item.setItemMeta(meta);
            inv.setItem(row * 9 + col, item);
        }

        if (page > 0) {
            ItemStack prevBtn = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevBtn.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "Previous Page");
            prevBtn.setItemMeta(prevMeta);
            inv.setItem(45, prevBtn);
        }
        if (endIndex < history.size()) {
            ItemStack nextBtn = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextBtn.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page");
            nextBtn.setItemMeta(nextMeta);
            inv.setItem(53, nextBtn);
        }

        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "Back to Auction House");
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

        p.openInventory(inv);
    }

    public static void openMyListings(Player p, AuctionManager manager, int page) {
        if (!p.hasPermission("lost.auction")) {
            p.sendMessage("You do not have permission to use the auction house.");
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, "Your Listings - Page " + (page + 1));
        fillBorder(inv);
        setBalanceItem(inv, p, manager, 4);

        List<Auction> mine = new ArrayList<>();
        for (Auction a : manager.listAuctions()) {
            if (a.seller.equals(p.getUniqueId())) mine.add(a);
        }
        int itemsPerPage = 28;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, mine.size());

        for (int i = startIndex; i < endIndex; i++) {
            int idx = i - startIndex;
            int row = idx / 7 + 1;
            int col = idx % 7 + 1;
            Auction a = mine.get(i);
            ItemStack item = a.item.clone();
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "ID: " + a.id);
            lore.add(ChatColor.GRAY + "Type: " + (a.type == Auction.Type.FIXED ? "Fixed Price" : "Auction"));
            if (a.type == Auction.Type.FIXED) {
                lore.add(ChatColor.GRAY + "Price: $" + a.startingPrice);
            } else {
                lore.add(ChatColor.GRAY + "Starting: $" + a.startingPrice);
                lore.add(ChatColor.GRAY + "Current bid: $" + a.currentBid + (a.currentBidder == null ? " (no bids)" : ""));
            }
            long remaining = a.endTime - System.currentTimeMillis();
            lore.add(ChatColor.GRAY + "Ends in: " + formatDuration(remaining));
            lore.add("");
            lore.add(ChatColor.RED + "Shift-click to cancel listing");
            if (a.currentBidder != null) {
                lore.add(ChatColor.YELLOW + "Current bidder will be refunded");
            }
            meta.setLore(lore);
            String itemName = meta.hasDisplayName() ? meta.getDisplayName() : formatMaterialName(item.getType());
            meta.setDisplayName(ChatColor.GREEN + itemName);
            item.setItemMeta(meta);
            inv.setItem(row * 9 + col, item);
        }

        if (page > 0) {
            ItemStack prevBtn = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevBtn.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "Previous Page");
            prevBtn.setItemMeta(prevMeta);
            inv.setItem(45, prevBtn);
        }
        if (endIndex < mine.size()) {
            ItemStack nextBtn = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextBtn.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page");
            nextBtn.setItemMeta(nextMeta);
            inv.setItem(53, nextBtn);
        }

        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "Back to Auction House");
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

        p.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClickMyListings(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (!title.startsWith("Your Listings - Page ")) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta == null || !clickedMeta.hasDisplayName()) return;
        String displayName = clickedMeta.getDisplayName();

        int currentPage = 0;
        try {
            currentPage = Integer.parseInt(title.substring("Your Listings - Page ".length())) - 1;
        } catch (NumberFormatException ignored) {}

        if (displayName.equals(ChatColor.RED + "Back to Auction House")) {
            openMain(p, manager);
            return;
        } else if (displayName.equals(ChatColor.YELLOW + "Previous Page")) {
            openMyListings(p, manager, Math.max(0, currentPage - 1));
            return;
        } else if (displayName.equals(ChatColor.YELLOW + "Next Page")) {
            openMyListings(p, manager, currentPage + 1);
            return;
        }

        int slot = e.getSlot();
        int row = slot / 9;
        int col = slot % 9;
        if (row < 1 || row > 4 || col < 1 || col > 7) return;
        int idx = (row - 1) * 7 + (col - 1);

        List<Auction> mine = new ArrayList<>();
        for (Auction a : manager.listAuctions()) {
            if (a.seller.equals(p.getUniqueId())) mine.add(a);
        }
        int auctionIndex = currentPage * 28 + idx;
        if (auctionIndex >= mine.size()) return;
        Auction a = mine.get(auctionIndex);

        if (!e.isShiftClick()) {
            p.sendMessage(ChatColor.YELLOW + "[Auction] Shift-click to confirm cancellation.");
            return;
        }

        if (a.currentBidder != null && a.currentBid > 0) {
            manager.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(a.currentBidder), a.currentBid);
            Player bidder = Bukkit.getPlayer(a.currentBidder);
            if (bidder != null && bidder.isOnline()) {
                bidder.sendMessage(ChatColor.YELLOW + "[Auction] A listing you bid on was cancelled. Refunded $" + a.currentBid);
            }
        }

        HashMap<Integer, ItemStack> overflow = p.getInventory().addItem(AuctionManager.stripCategoryLore(a.item));
        for (ItemStack drop : overflow.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), drop);
        }
        manager.removeAuction(a.id);
        p.sendMessage(ChatColor.GREEN + "[Auction] Cancelled listing and recovered your item.");
        openMyListings(p, manager, currentPage);
    }

    @EventHandler
    public void onInventoryClickSearch(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("Search & Filter Auctions")) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (!p.hasPermission("lost.auction")) { p.sendMessage("You do not have permission to use the auction house. :|"); p.closeInventory(); return; }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta != null && clickedMeta.hasDisplayName()) {
            String displayName = clickedMeta.getDisplayName();
            if (displayName.equals(ChatColor.YELLOW + "Search by Item Name")) {
                AnvilListener.openAnvilForSearch(p, manager);
            } else if (displayName.equals(ChatColor.GOLD + "Filter by Price Range")) {
                // I don't really know to to get price filtering to work yet, but I hope to add it soon.
                p.sendMessage("[Auction] Price filtering isn't here yet, but might be soon. -Minty"); // Might just disable/hide the button for it
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
    @EventHandler
    public void onInventoryClickBrowse(InventoryClickEvent e) {
        if (!e.getView().getTitle().startsWith("Browse Auctions - Page ")) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (!p.hasPermission("lost.auction")) { p.sendMessage("You do not have permission to use the auction house."); p.closeInventory(); return; }
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta != null && clickedMeta.hasDisplayName()) {
            String displayName = clickedMeta.getDisplayName();
            if (displayName.equals(ChatColor.YELLOW + "Previous Page")) {
                int currentPage = Integer.parseInt(e.getView().getTitle().substring("Browse Auctions - Page ".length())) - 1;
                openBrowseAuctions(p, manager, currentPage - 1);
                return;
            } else if (displayName.equals(ChatColor.YELLOW + "Next Page")) {
                int currentPage = Integer.parseInt(e.getView().getTitle().substring("Browse Auctions - Page ".length())) - 1;
                openBrowseAuctions(p, manager, currentPage + 1);
                return;
            } else if (displayName.equals(ChatColor.RED + "Back to Menu")) {
                openMain(p, manager);
                return;
            }
        }
        int slot = e.getSlot();
        // Only interior slots: rows 1-4, cols 1-7
        if (slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8) return;
        int itemIndex = ((slot / 9) - 1) * 7 + (slot % 9 - 1);
        List<Auction> auctions = new ArrayList<>(manager.listAuctions());
        int currentPage = Integer.parseInt(e.getView().getTitle().substring("Browse Auctions - Page ".length())) - 1;
        int auctionIndex = currentPage * 28 + itemIndex;
        if (auctionIndex >= auctions.size()) return;
        Auction a = auctions.get(auctionIndex);

        if (a.seller.equals(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "[Auction] You cannot bid on or buy your own listing.");
            return;
        }

        if (a.type == Auction.Type.FIXED) {
            double price = a.startingPrice;
            if (!manager.getEconomy().has(p, price)) { p.sendMessage("You cannot afford this item."); return; }
            manager.getEconomy().withdrawPlayer(p, price);
            manager.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(a.seller), price);
            if (p.isOnline()) p.getInventory().addItem(AuctionManager.stripCategoryLore(a.item));
            manager.removeAuction(a.id);
            p.sendMessage("[Auction] You bought item for " + price);
        } else {
            AnvilListener.openAnvilForBid(p, a, manager);
        }
    }
    public static void openTypeFilter(Player p, AuctionManager manager) {
        Inventory inv = Bukkit.createInventory(null, 27, "Filter by Auction Type");

        // But it now button
        ItemStack fixedBtn = new ItemStack(Material.PAPER);
        ItemMeta fixedMeta = fixedBtn.getItemMeta();
        fixedMeta.setDisplayName(ChatColor.GOLD + "Show Buy it now Only");
        List<String> fLore = new ArrayList<>();
        fLore.add("Buy-it-now items");
        fixedMeta.setLore(fLore);
        fixedBtn.setItemMeta(fixedMeta);
        inv.setItem(11, fixedBtn);

        // Auction button
        ItemStack auctionBtn = new ItemStack(Material.ANVIL);
        ItemMeta auctionMeta = auctionBtn.getItemMeta();
        auctionMeta.setDisplayName(ChatColor.GREEN + "Show Auctions Only");
        List<String> aLore = new ArrayList<>();
        aLore.add("Click to show only auctions");
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
                openFilteredAuctions(p, manager, Auction.Type.FIXED, 0);
            } else if (displayName.equals(ChatColor.GREEN + "Show Auctions Only")) {
                openFilteredAuctions(p, manager, Auction.Type.AUCTION, 0);
            } else if (displayName.equals(ChatColor.RED + "Back")) {
                openSearch(p, manager);
            }
        }
    }

    public static void openFilteredAuctions(Player p, AuctionManager manager, Auction.Type filterType, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, "Filtered Auctions: " + filterType.name() + " - Page " + (page + 1));
        List<Auction> filteredAuctions = new ArrayList<>();
        for (Auction a : manager.listAuctions()) {
            if (a.type == filterType) filteredAuctions.add(a);
        }
        int itemsPerPage = 45;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, filteredAuctions.size());
        
        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            Auction a = filteredAuctions.get(i);
            ItemStack item = a.item.clone();
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
            List<String> lore = new ArrayList<>();
            if (filterType == Auction.Type.FIXED) lore.add("Price: " + a.startingPrice);
            else lore.add("Current bid: " + a.currentBid + (a.currentBidder == null ? " (no bids)" : ""));
            long remaining = a.endTime - System.currentTimeMillis();
            lore.add("Ends in: " + formatDuration(remaining));
            meta.setLore(lore);
            String itemName = meta.hasDisplayName() ? meta.getDisplayName() : formatMaterialName(item.getType());
            meta.setDisplayName(ChatColor.GREEN + itemName);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }

        // Previous page button
        if (page > 0) {
            ItemStack prevBtn = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevBtn.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "Previous Page");
            prevBtn.setItemMeta(prevMeta);
            inv.setItem(48, prevBtn);
        }
        
        // Next page button
        if (endIndex < filteredAuctions.size()) {
            ItemStack nextBtn = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextBtn.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page");
            nextBtn.setItemMeta(nextMeta);
            inv.setItem(50, nextBtn);
        }
        
        // Back button
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "Back to Search");
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

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
        if (clickedMeta != null && clickedMeta.hasDisplayName()) {
            String displayName = clickedMeta.getDisplayName();
            if (displayName.equals(ChatColor.YELLOW + "Previous Page")) {
                String title = e.getView().getTitle();
                Auction.Type filterType = title.contains("FIXED") ? Auction.Type.FIXED : Auction.Type.AUCTION;
                int currentPage = Integer.parseInt(title.substring(title.lastIndexOf(" ") + 1)) - 1;
                openFilteredAuctions(p, manager, filterType, currentPage - 1);
                return;
            } else if (displayName.equals(ChatColor.YELLOW + "Next Page")) {
                String title = e.getView().getTitle();
                Auction.Type filterType = title.contains("FIXED") ? Auction.Type.FIXED : Auction.Type.AUCTION;
                int currentPage = Integer.parseInt(title.substring(title.lastIndexOf(" ") + 1)) - 1;
                openFilteredAuctions(p, manager, filterType, currentPage + 1);
                return;
            } else if (displayName.equals(ChatColor.RED + "Back to Search")) {
                openSearch(p, manager);
                return;
            }
        }

        // Handle auction clicks
        int slot = e.getSlot();
        if (slot < 0 || slot >= 45) return; // 45 items per page

        List<Auction> auctionList = new ArrayList<>();
        String title = e.getView().getTitle();
        Auction.Type filterType = title.contains("FIXED") ? Auction.Type.FIXED : Auction.Type.AUCTION;
        int currentPage = Integer.parseInt(title.substring(title.lastIndexOf(" ") + 1)) - 1;
        int startIndex = currentPage * 45;

        for (Auction a : manager.listAuctions()) {
            if (a.type == filterType) auctionList.add(a);
        }

        int auctionIndex = startIndex + slot;
        if (auctionIndex >= auctionList.size()) return;
        Auction a = auctionList.get(auctionIndex);

        if (a.seller.equals(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "[Auction] You cannot bid on or buy your own listing.");
            return;
        }

        if (a.type == Auction.Type.FIXED) {
            double price = a.startingPrice;
            if (!manager.getEconomy().has(p, price)) { p.sendMessage("You cannot afford this item."); return; }
            manager.getEconomy().withdrawPlayer(p, price);
            manager.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(a.seller), price);
            // deliver item
            if (p.isOnline()) p.getInventory().addItem(AuctionManager.stripCategoryLore(a.item));
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
        if (clickedMeta != null && clickedMeta.hasDisplayName()) {
            String displayName = clickedMeta.getDisplayName();
            if (displayName.equals(ChatColor.YELLOW + "Previous Page")) {
                String title = e.getView().getTitle();
                String searchTerm = title.substring(title.indexOf("'") + 1, title.lastIndexOf("'"));
                int currentPage = Integer.parseInt(title.substring(title.lastIndexOf(" ") + 1)) - 1;
                openSearchResults(p, manager, searchTerm, currentPage - 1);
                return;
            } else if (displayName.equals(ChatColor.YELLOW + "Next Page")) {
                String title = e.getView().getTitle();
                String searchTerm = title.substring(title.indexOf("'") + 1, title.lastIndexOf("'"));
                int currentPage = Integer.parseInt(title.substring(title.lastIndexOf(" ") + 1)) - 1;
                openSearchResults(p, manager, searchTerm, currentPage + 1);
                return;
            } else if (displayName.equals(ChatColor.RED + "Back to Search")) {
                openSearch(p, manager);
                return;
            }
        }

        // Handle auction clicks
        int slot = e.getSlot();
        if (slot < 0 || slot >= 45) return; // 45 items per page

        String title = e.getView().getTitle();
        String searchTerm = title.substring(title.indexOf("'") + 1, title.lastIndexOf("'"));
        int currentPage = Integer.parseInt(title.substring(title.lastIndexOf(" ") + 1)) - 1;
        int startIndex = currentPage * 45;

        List<Auction> searchResults = new ArrayList<>();
        for (Auction a : manager.listAuctions()) {
            String itemName = a.item.getType().name().toLowerCase();
            if (a.item.hasItemMeta() && a.item.getItemMeta().hasDisplayName()) {
                itemName = a.item.getItemMeta().getDisplayName().toLowerCase();
            }
            if (itemName.contains(searchTerm.toLowerCase())) {
                searchResults.add(a);
            }
        }

        int auctionIndex = startIndex + slot;
        if (auctionIndex >= searchResults.size()) return;
        Auction a = searchResults.get(auctionIndex);

        if (a.seller.equals(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "[Auction] You cannot bid on or buy your own listing.");
            return;
        }

        if (a.type == Auction.Type.FIXED) {
            double price = a.startingPrice;
            if (!manager.getEconomy().has(p, price)) { p.sendMessage("You cannot afford this item."); return; }
            manager.getEconomy().withdrawPlayer(p, price);
            manager.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(a.seller), price);
            // deliver item
            if (p.isOnline()) p.getInventory().addItem(AuctionManager.stripCategoryLore(a.item));
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
                openFilteredCategoryAuctions(p, manager, category, 0);
                return;
            }
        }
    }

    public static void openFilteredCategoryAuctions(Player p, AuctionManager manager, String category, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, "Category: " + category + " - Page " + (page + 1));
        fillBorder(inv);
        setBalanceItem(inv, p, manager, 4);

        List<Auction> categoryResults = new ArrayList<>();
        for (Auction a : manager.listAuctions()) {
            if (category.equals(a.category)) categoryResults.add(a);
        }
        int itemsPerPage = 28;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, categoryResults.size());

        for (int i = startIndex; i < endIndex; i++) {
            int idx = i - startIndex;
            int row = idx / 7 + 1;
            int col = idx % 7 + 1;
            Auction a = categoryResults.get(i);
            ItemStack item = a.item.clone();
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
            List<String> lore = new ArrayList<>();
            if (a.type == Auction.Type.FIXED) lore.add("Price: " + a.startingPrice);
            else lore.add("Current bid: " + a.currentBid + (a.currentBidder == null ? " (no bids)" : ""));
            long remaining = a.endTime - System.currentTimeMillis();
            lore.add("Ends in: " + formatDuration(remaining));
            lore.add("Category: " + a.category);
            meta.setLore(lore);
            String itemName = meta.hasDisplayName() ? meta.getDisplayName() : formatMaterialName(item.getType());
            meta.setDisplayName(ChatColor.GREEN + itemName);
            item.setItemMeta(meta);
            inv.setItem(row * 9 + col, item);
        }

        if (page > 0) {
            ItemStack prevBtn = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevBtn.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "Previous Page");
            prevBtn.setItemMeta(prevMeta);
            inv.setItem(45, prevBtn);
        }

        if (endIndex < categoryResults.size()) {
            ItemStack nextBtn = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextBtn.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page");
            nextBtn.setItemMeta(nextMeta);
            inv.setItem(53, nextBtn);
        }

        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "Back to Search");
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

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
        if (clickedMeta != null && clickedMeta.hasDisplayName()) {
            String displayName = clickedMeta.getDisplayName();
            if (displayName.equals(ChatColor.YELLOW + "Previous Page")) {
                String title = e.getView().getTitle();
                String category = title.substring(title.indexOf(": ") + 2, title.lastIndexOf(" - Page"));
                int currentPage = Integer.parseInt(title.substring(title.lastIndexOf(" ") + 1)) - 1;
                openFilteredCategoryAuctions(p, manager, category, currentPage - 1);
                return;
            } else if (displayName.equals(ChatColor.YELLOW + "Next Page")) {
                String title = e.getView().getTitle();
                String category = title.substring(title.indexOf(": ") + 2, title.lastIndexOf(" - Page"));
                int currentPage = Integer.parseInt(title.substring(title.lastIndexOf(" ") + 1)) - 1;
                openFilteredCategoryAuctions(p, manager, category, currentPage + 1);
                return;
            } else if (displayName.equals(ChatColor.RED + "Back to Search")) {
                openSearch(p, manager);
                return;
            }
        }

        // Handle auction clicks (interior 7x4 grid: rows 1-4, cols 1-7)
        int slot = e.getSlot();
        int row = slot / 9;
        int col = slot % 9;
        if (row < 1 || row > 4 || col < 1 || col > 7) return;
        int idx = (row - 1) * 7 + (col - 1);

        String title = e.getView().getTitle();
        String category = title.substring(title.indexOf(": ") + 2, title.lastIndexOf(" - Page"));
        int currentPage = Integer.parseInt(title.substring(title.lastIndexOf(" ") + 1)) - 1;
        int startIndex = currentPage * 28;

        List<Auction> categoryResults = new ArrayList<>();
        for (Auction a : manager.listAuctions()) {
            if (category.equals(a.category)) {
                categoryResults.add(a);
            }
        }

        int auctionIndex = startIndex + idx;
        if (auctionIndex >= categoryResults.size()) return;
        Auction a = categoryResults.get(auctionIndex);

        if (a.seller.equals(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "[Auction] You cannot bid on or buy your own listing.");
            return;
        }

        if (a.type == Auction.Type.FIXED) {
            double price = a.startingPrice;
            if (!manager.getEconomy().has(p, price)) { p.sendMessage("You cannot afford this item."); return; }
            manager.getEconomy().withdrawPlayer(p, price);
            manager.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(a.seller), price);
            // deliver item
            if (p.isOnline()) p.getInventory().addItem(AuctionManager.stripCategoryLore(a.item));
            manager.removeAuction(a.id);
            p.sendMessage("[Auction] You bought item for " + price);
        } else {
            // open anvil to input bid
            AnvilListener.openAnvilForBid(p, a, manager);
        }
    }

    private static ItemStack makePriceDisplay(long price) {
        ItemStack item = new ItemStack(Material.SUNFLOWER);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(ChatColor.GOLD + "$" + price);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Current listing price");
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

    private static ItemStack makeAdjustButton(Material mat, ChatColor color, String label) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(color + label);
        item.setItemMeta(m);
        return item;
    }

    public static void openSetPrice(Player p, AuctionManager manager) {
        ItemStack item = pendingSelection.get(p);
        String category = pendingCategory.get(p);
        Auction.Type type = pendingListingType.get(p);
        if (item == null || category == null || type == null) {
            p.sendMessage("No item selected for listing.");
            p.closeInventory();
            return;
        }
        pendingPrice.putIfAbsent(p, 100L);

        Inventory inv = Bukkit.createInventory(null, 54, "Set Listing Price");
        fillBorder(inv);
        setBalanceItem(inv, p, manager, 4);

        ItemStack preview = item.clone();
        ItemMeta previewMeta = preview.hasItemMeta() ? preview.getItemMeta() : Bukkit.getItemFactory().getItemMeta(preview.getType());
        List<String> previewLore = new ArrayList<>();
        if (previewMeta.getLore() != null) {
            for (String line : previewMeta.getLore()) {
                if (!line.startsWith("CATEGORY:")) previewLore.add(line);
            }
        }
        previewLore.add(ChatColor.YELLOW + "Item to be listed");
        previewMeta.setLore(previewLore);
        preview.setItemMeta(previewMeta);
        inv.setItem(13, preview);

        inv.setItem(19, makeAdjustButton(Material.RED_STAINED_GLASS_PANE, ChatColor.RED, "-100"));
        inv.setItem(20, makeAdjustButton(Material.RED_STAINED_GLASS_PANE, ChatColor.RED, "-10"));
        inv.setItem(21, makeAdjustButton(Material.RED_STAINED_GLASS_PANE, ChatColor.RED, "-1"));
        inv.setItem(22, makePriceDisplay(pendingPrice.get(p)));
        inv.setItem(23, makeAdjustButton(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN, "+1"));
        inv.setItem(24, makeAdjustButton(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN, "+10"));
        inv.setItem(25, makeAdjustButton(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN, "+100"));

        ItemStack infoItem = new ItemStack(type == Auction.Type.FIXED ? Material.PAPER : Material.ANVIL);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.AQUA + (type == Auction.Type.FIXED ? "Fixed Price Listing" : "Auction Listing"));
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Category: " + category);
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(31, infoItem);

        ItemStack cancelBtn = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancelBtn.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "Cancel Listing");
        cancelBtn.setItemMeta(cancelMeta);
        inv.setItem(48, cancelBtn);

        ItemStack confirmBtn = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta confirmMeta = confirmBtn.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.GREEN + "Confirm Listing");
        confirmBtn.setItemMeta(confirmMeta);
        inv.setItem(50, confirmBtn);

        p.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClickSetPrice(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("Set Listing Price")) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta == null || !clickedMeta.hasDisplayName()) return;
        String displayName = clickedMeta.getDisplayName();

        if (displayName.equals(ChatColor.RED + "Cancel Listing")) {
            ItemStack item = pendingSelection.remove(p);
            pendingCategory.remove(p);
            pendingListingType.remove(p);
            pendingPrice.remove(p);
            if (item != null) {
                HashMap<Integer, ItemStack> overflow = p.getInventory().addItem(AuctionManager.stripCategoryLore(item));
                for (ItemStack drop : overflow.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }
            }
            p.sendMessage(ChatColor.YELLOW + "[Auction] Cancelled listing.");
            p.closeInventory();
            return;
        }

        if (displayName.equals(ChatColor.GREEN + "Confirm Listing")) {
            ItemStack item = pendingSelection.remove(p);
            String category = pendingCategory.remove(p);
            Auction.Type type = pendingListingType.remove(p);
            Long price = pendingPrice.remove(p);
            if (item == null || category == null || type == null || price == null) {
                p.sendMessage("No listing in progress.");
                p.closeInventory();
                return;
            }
            long duration = manager.getPlugin().getConfig().getLong("default-duration-hours", 24) * 3600L * 1000L;
            manager.createAuction(p.getUniqueId(), item.clone(), type, price, duration, category);
            p.sendMessage(ChatColor.GREEN + "[Auction] Listed for $" + price + "!");
            p.closeInventory();
            return;
        }

        long delta = 0;
        if (clicked.getType() == Material.LIME_STAINED_GLASS_PANE && displayName.startsWith(ChatColor.GREEN + "+")) {
            try { delta = Long.parseLong(displayName.substring((ChatColor.GREEN + "+").length())); } catch (NumberFormatException ex) { return; }
        } else if (clicked.getType() == Material.RED_STAINED_GLASS_PANE && displayName.startsWith(ChatColor.RED + "-")) {
            try { delta = -Long.parseLong(displayName.substring((ChatColor.RED + "-").length())); } catch (NumberFormatException ex) { return; }
        } else {
            return;
        }

        long current = pendingPrice.getOrDefault(p, 100L);
        long updated = Math.max(1, current + delta);
        pendingPrice.put(p, updated);
        e.getInventory().setItem(22, makePriceDisplay(updated));
    }

    @EventHandler
    public void onInventoryClickBrowseCategories(InventoryClickEvent e) {
        if (!e.getView().getTitle().startsWith("Browse Categories - Page ")) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (!p.hasPermission("lost.auction")) { p.sendMessage("You do not have permission to use the auction house."); p.closeInventory(); return; }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta == null || !clickedMeta.hasDisplayName()) return;

        String displayName = clickedMeta.getDisplayName();
        int currentPage = Integer.parseInt(e.getView().getTitle().substring("Browse Categories - Page ".length())) - 1;

        if (displayName.equals(ChatColor.YELLOW + "Previous Page")) {
            openBrowseCategories(p, manager, currentPage - 1);
        } else if (displayName.equals(ChatColor.YELLOW + "Next Page")) {
            openBrowseCategories(p, manager, currentPage + 1);
        } else if (displayName.equals(ChatColor.RED + "Back to Auction House")) {
            openMain(p, manager);
        } else if (displayName.startsWith(ChatColor.GREEN.toString())) {
            String category = displayName.substring(ChatColor.GREEN.toString().length());
            openFilteredCategoryAuctions(p, manager, category, 0);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("Auction House")) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (!p.hasPermission("lost.auction")) { p.sendMessage("You do not have permission to use the auction house."); p.closeInventory(); return; }
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta != null && clickedMeta.hasDisplayName()) {
            if (clickedMeta.getDisplayName().equals(ChatColor.GREEN + "Browse All Auctions")) {
                openBrowseAuctions(p, manager, 0);
                return;
            } else if (clickedMeta.getDisplayName().equals(ChatColor.LIGHT_PURPLE + "Browse Categories")) {
                openBrowseCategories(p, manager, 0);
                return;
            } else if (clickedMeta.getDisplayName().equals(ChatColor.YELLOW + "Search & Filter")) {
                openSearch(p, manager);
                return;
            } else if (clickedMeta.getDisplayName().equals(ChatColor.BLUE + "Your Auction History")) {
                openHistory(p, manager, 0);
                return;
            } else if (clickedMeta.getDisplayName().equals(ChatColor.AQUA + "List Item for Sale")) {
                openSelectItem(p, manager);
                return;
            } else if (clickedMeta.getDisplayName().equals(ChatColor.GOLD + "My Listings")) {
                openMyListings(p, manager, 0);
                return;
            }
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
                // Return to item selection
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
        if (!p.hasPermission("lost.auction")) { p.sendMessage("You do not have permission to use the auction house."); p.closeInventory(); return; }

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
            ItemStack sel = pendingSelection.get(p);
            if (sel == null) { p.sendMessage("No item selected to list."); p.closeInventory(); return; }

            // Extract category from item lore
            String category = "General";
            ItemMeta itemMeta = sel.getItemMeta();
            if (itemMeta != null && itemMeta.getLore() != null) {
                for (String line : itemMeta.getLore()) {
                    if (line.startsWith("CATEGORY:")) {
                        category = line.substring("CATEGORY:".length());
                        break;
                    }
                }
            }

            if (clicked.getType() == Material.PAPER) {
                pendingCategory.put(p, category);
                pendingListingType.put(p, Auction.Type.FIXED);
                openSetPrice(p, manager);
            } else if (clicked.getType() == Material.ANVIL) {
                pendingCategory.put(p, category);
                pendingListingType.put(p, Auction.Type.AUCTION);
                openSetPrice(p, manager);
            } else {
                pendingSelection.remove(p);
                p.getInventory().addItem(AuctionManager.stripCategoryLore(sel));
                p.sendMessage("Cancelled listing.");
                p.closeInventory();
            }
        }
    }

    @EventHandler
    public void onInventoryClickAdmin(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("Auction Admin")) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (!p.hasPermission("lost.auction.admin")) { p.sendMessage("You do not have permission to use the auction admin GUI."); p.closeInventory(); return; }
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
    public void onListingFlowClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        String title = e.getView().getTitle();
        if (!title.equals("Choose Category")
                && !title.equals("Choose Listing Type")
                && !title.equals("Set Listing Price")) return;

        Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
            String newTitle = p.getOpenInventory().getTitle();
            if (newTitle.equals("Choose Category")
                    || newTitle.equals("Choose Listing Type")
                    || newTitle.equals("Set Listing Price")
                    || newTitle.equals("Select Item to List")) {
                return;
            }
            ItemStack item = pendingSelection.remove(p);
            pendingCategory.remove(p);
            pendingListingType.remove(p);
            pendingPrice.remove(p);
            if (item != null) {
                HashMap<Integer, ItemStack> overflow = p.getInventory().addItem(AuctionManager.stripCategoryLore(item));
                for (ItemStack drop : overflow.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }
                p.sendMessage(ChatColor.YELLOW + "[Auction] Returned item from cancelled listing.");
            }
        });
    }

    @EventHandler
    public void onInventoryClickHistory(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        boolean isPlayerHistory = title.startsWith("Your Auction History");
        boolean isAllHistory = title.startsWith("All Auction History");
        if (!isPlayerHistory && !isAllHistory) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta == null || !clickedMeta.hasDisplayName()) return;
        String displayName = clickedMeta.getDisplayName();

        int currentPage = 0;
        int pageIdx = title.lastIndexOf(" - Page ");
        if (pageIdx >= 0) {
            try {
                currentPage = Integer.parseInt(title.substring(pageIdx + 8)) - 1;
            } catch (NumberFormatException ignored) {}
        }

        if (displayName.equals(ChatColor.RED + "Back to Auction House")) {
            openMain(p, manager);
        } else if (displayName.equals(ChatColor.YELLOW + "Previous Page")) {
            if (isPlayerHistory) openHistory(p, manager, Math.max(0, currentPage - 1));
            else openAllHistory(p, manager, Math.max(0, currentPage - 1));
        } else if (displayName.equals(ChatColor.YELLOW + "Next Page")) {
            if (isPlayerHistory) openHistory(p, manager, currentPage + 1);
            else openAllHistory(p, manager, currentPage + 1);
        }
    }
}
