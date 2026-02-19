package com.betafish.auctionhouse;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

public class AnvilListener implements Listener {
    private static final Map<Player, String> awaitingAuction = new HashMap<>();
    private static final Map<Player, PendingListing> awaitingListing = new HashMap<>();
    private static final Map<Player, Boolean> awaitingSearch = new HashMap<>();
    private final AuctionManager manager;

    public AnvilListener(AuctionManager manager) { this.manager = manager; }

    private static class PendingListing {
        public final ItemStack item;
        public final Auction.Type type;
        public final AuctionManager manager;
        public final String category;
        public PendingListing(ItemStack item, Auction.Type type, AuctionManager manager, String category) {
            this.item = item;
            this.type = type;
            this.manager = manager;
            this.category = category;
        }
    }

    public static void openAnvilForBid(Player p, Auction a, AuctionManager manager) {
        p.sendMessage("\n\u00A76Auction House\n\u00A7ePlease enter your bid amount in chat. \n");
        p.closeInventory();
        awaitingAuction.put(p, a.id);
    }

    public static void openAnvilForSearch(Player p, AuctionManager manager) {
        p.sendMessage("\n\u00A76Auction House Search\n\u00A7ePlease enter the item name in chat. \n");
        p.closeInventory();
        awaitingSearch.put(p, true);
    }
// Todo: Figure out how to do it without chat.
    public static void openAnvilForListing(Player p, ItemStack item, AuctionManager manager, Auction.Type type, String category) {
        p.sendMessage("\n\u00A76Auction House\n\u00A7ePlease enter the price in chat. \n");
        p.closeInventory();
        awaitingListing.put(p, new PendingListing(item.clone(), type, manager, category));
    }

    @EventHandler
    public void onAnvilClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("Place bid amount")) return;
        if (!(e.getWhoClicked() instanceof Player)) return;
        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        if (!p.hasPermission("beta.auction")) { p.sendMessage("You do not have permission to use the auction house."); p.closeInventory(); return; }
        ItemStack result = e.getCurrentItem();
        if (result == null || !result.hasItemMeta()) return;
        String name = result.getItemMeta().getDisplayName();
        if (name == null) return;
        String raw = name.replaceAll("[^0-9\\.]", "");
        double bid;
        try { bid = Double.parseDouble(raw); } catch (Exception ex) { p.sendMessage("Invalid bid"); awaitingAuction.remove(p); p.closeInventory(); return; }

        String aid = awaitingAuction.remove(p);
        if (aid == null) { p.sendMessage("No auction selected"); p.closeInventory(); return; }
        Auction a = manager.getAuction(aid);
        if (a == null) { p.sendMessage("Auction not found"); p.closeInventory(); return; }

        double minIncrement = manager.getPlugin().getConfig().getDouble("anvil-bid-min-increment", 1.0);
        double required = Math.max(a.startingPrice, a.currentBid + minIncrement);
        if (bid < required) { p.sendMessage("Your bid must be at least " + required); p.closeInventory(); return; }

        if (!manager.getEconomy().has(p, bid)) { p.sendMessage("You cannot afford this bid."); p.closeInventory(); return; }

        // withdraw bidder
        manager.getEconomy().withdrawPlayer(p, bid);
        // refund previous bidder
        if (a.currentBidder != null) {
            manager.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(a.currentBidder), a.currentBid);
        }

        a.currentBid = bid;
        a.currentBidder = p.getUniqueId();
        manager.save();
        p.sendMessage(ChatColor.GREEN + "Bid placed: " + bid);
        p.closeInventory();
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        // first, handle listing input if present
        if (awaitingListing.containsKey(p)) {
            e.setCancelled(true);
            String msg = e.getMessage();
            Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
                String raw = msg.replaceAll("[^0-9\\.]", "");
                double price;
                try { price = Double.parseDouble(raw); } catch (Exception ex) {
                    p.sendMessage("Invalid price, listing cancelled.");
                    PendingListing pl = awaitingListing.remove(p);
                    if (pl != null) {
                        // return item to player as pending delivery (safe)
                        pl.manager.addPendingDelivery(p.getUniqueId(), pl.item);
                    }
                    return;
                }
                PendingListing pl = awaitingListing.remove(p);
                if (pl == null) { p.sendMessage("No listing in progress"); return; }
                AuctionManager mgr = pl.manager;
                long duration = mgr.getPlugin().getConfig().getLong("default-duration-hours", 24) * 3600L * 1000L;
                mgr.createAuction(p.getUniqueId(), pl.item.clone(), pl.type, price, duration, pl.category);
                p.sendMessage(ChatColor.GREEN + "Listing created: " + price);
            });
            return;
        }

        if (awaitingSearch.containsKey(p)) {
            e.setCancelled(true); // hide message from others
            String searchTerm = e.getMessage().toLowerCase().trim();
            awaitingSearch.remove(p);

            // process on main thread
            Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
                AuctionGUI.openSearchResults(p, manager, searchTerm);
            });
            return;
        }

        if (!awaitingAuction.containsKey(p)) return;
        e.setCancelled(true); // hide message from others
        String msg = e.getMessage();
        // process on main thread because economy and inventory must be accessed sync
        Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
            String raw = msg.replaceAll("[^0-9\\.]", "");
            double bid;
            try { bid = Double.parseDouble(raw); } catch (Exception ex) { p.sendMessage("Invalid bid"); awaitingAuction.remove(p); return; }

            String aid = awaitingAuction.remove(p);
            if (aid == null) { p.sendMessage("No auction selected"); return; }
            Auction a = manager.getAuction(aid);
            if (a == null) { p.sendMessage("Auction not found"); return; }

            double minIncrement = manager.getPlugin().getConfig().getDouble("anvil-bid-min-increment", 1.0);
            double required = Math.max(a.startingPrice, a.currentBid + minIncrement);
            if (bid < required) { p.sendMessage("Your bid must be at least " + required); return; }

            if (!manager.getEconomy().has(p, bid)) { p.sendMessage("You cannot afford this bid."); return; }

            // withdraw bidder
            manager.getEconomy().withdrawPlayer(p, bid);
            // refund previous bidder
            if (a.currentBidder != null) {
                manager.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(a.currentBidder), a.currentBid);
            }

            a.currentBid = bid;
            a.currentBidder = p.getUniqueId();
            manager.save();
            p.sendMessage(ChatColor.GREEN + "Bid placed: " + bid);
            p.closeInventory();
        });
    }
}
