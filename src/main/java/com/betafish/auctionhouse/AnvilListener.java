package com.betafish.auctionhouse;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AnvilListener implements Listener {
    private static final Map<Player, String> awaitingAuction = new ConcurrentHashMap<>();
    private static final Map<Player, Boolean> awaitingSearch = new ConcurrentHashMap<>();
    public static final Map<Player, Boolean> awaitingPriceFilterMin = new ConcurrentHashMap<>();
    public static final Map<Player, Boolean> awaitingPriceFilterMax = new ConcurrentHashMap<>();
    public static final Map<Player, String> awaitingAdminBid = new ConcurrentHashMap<>();
    private final AuctionManager manager;

    public AnvilListener(AuctionManager manager) { this.manager = manager; }

    public static void openAnvilForBid(Player p, Auction a, AuctionManager manager) {
        if (a.seller.equals(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "[Auction] You cannot bid on your own listing.");
            p.closeInventory();
            return;
        }
        p.sendMessage("\n\u00A76Auction House\n\u00A7ePlease enter your bid amount in chat. \n");
        p.closeInventory();
        awaitingAuction.put(p, a.id);
    }

    public static void openAnvilForSearch(Player p, AuctionManager manager) {
        p.sendMessage("\n\u00A76Auction House Search\n\u00A7ePlease enter the item name in chat. \n");
        p.closeInventory();
        awaitingSearch.put(p, true);
    }

    public static void openAnvilForPriceFilterMin(Player p, AuctionManager manager) {
        p.sendMessage("\n\u00A76Auction House Price Filter\n\u00A7eEnter the minimum price in chat (or type 'none' for no minimum): \n");
        p.closeInventory();
        awaitingPriceFilterMin.put(p, true);
    }

    public static void openAnvilForPriceFilterMax(Player p, AuctionManager manager) {
        p.sendMessage("\n\u00A76Auction House Price Filter\n\u00A7eEnter the maximum price in chat (or type 'none' for no maximum): \n");
        p.closeInventory();
        awaitingPriceFilterMax.put(p, true);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();

        if (awaitingSearch.containsKey(p)) {
            e.setCancelled(true);
            String searchTerm = e.getMessage().toLowerCase().trim();
            awaitingSearch.remove(p);

            Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
                AuctionGUI.openSearchResults(p, manager, searchTerm, 0);
            });
            return;
        }

        if (awaitingPriceFilterMin.containsKey(p)) {
            e.setCancelled(true);
            String msg = e.getMessage();
            awaitingPriceFilterMin.remove(p);
            Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
                String raw = msg.replaceAll("[^0-9\\.]", "");
                double price;
                if (msg.equalsIgnoreCase("none")) {
                    AuctionGUI.setPriceFilterMin(p, 0.0);
                } else {
                    try { price = Double.parseDouble(raw); } catch (Exception ex) {
                        p.sendMessage("Invalid price.");
                        return;
                    }
                    if (!Double.isFinite(price) || price < 0) {
                        p.sendMessage("Invalid price.");
                        return;
                    }
                    AuctionGUI.setPriceFilterMin(p, price);
                }
                AuctionGUI.openPriceFilter(p, manager);
            });
            return;
        }

        if (awaitingPriceFilterMax.containsKey(p)) {
            e.setCancelled(true);
            String msg = e.getMessage();
            awaitingPriceFilterMax.remove(p);
            Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
                String raw = msg.replaceAll("[^0-9\\.]", "");
                double price;
                if (msg.equalsIgnoreCase("none")) {
                    AuctionGUI.setPriceFilterMax(p, Double.MAX_VALUE);
                } else {
                    try { price = Double.parseDouble(raw); } catch (Exception ex) {
                        p.sendMessage("Invalid price.");
                        return;
                    }
                    if (!Double.isFinite(price) || price < 0) {
                        p.sendMessage("Invalid price.");
                        return;
                    }
                    AuctionGUI.setPriceFilterMax(p, price);
                }
                AuctionGUI.openPriceFilter(p, manager);
            });
            return;
        }

        if (awaitingAdminBid.containsKey(p)) {
            e.setCancelled(true);
            String msg = e.getMessage();
            Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
                String raw = msg.replaceAll("[^0-9\\.]", "");
                double bid;
                try { bid = Double.parseDouble(raw); } catch (Exception ex) {
                    p.sendMessage("Invalid amount.");
                    awaitingAdminBid.remove(p);
                    return;
                }
                double maxCap = manager.getPlugin().getConfig().getDouble("max-bid-cap", 999000000);
                if (!Double.isFinite(bid) || bid <= 0 || bid > maxCap) {
                    p.sendMessage("Invalid amount.");
                    awaitingAdminBid.remove(p);
                    return;
                }
                String aid = awaitingAdminBid.remove(p);
                if (aid == null) return;
                if (manager.changeBid(aid, bid))
                    p.sendMessage("[AuctionAdmin] Bid/price changed to " + bid + " for auction " + aid + ".");
                else
                    p.sendMessage("[AuctionAdmin] Auction not found.");
            });
            return;
        }

        if (!awaitingAuction.containsKey(p)) return;
        e.setCancelled(true);
        String msg = e.getMessage();
        Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
            String raw = msg.replaceAll("[^0-9\\.]", "");
            double bid;
            try { bid = Double.parseDouble(raw); } catch (Exception ex) { p.sendMessage("Invalid bid"); awaitingAuction.remove(p); return; }

            double maxCap = manager.getPlugin().getConfig().getDouble("max-bid-cap", 999000000);
            if (!Double.isFinite(bid) || bid <= 0 || bid > maxCap) { p.sendMessage("Invalid bid amount"); awaitingAuction.remove(p); return; }

            String aid = awaitingAuction.remove(p);
            if (aid == null) { p.sendMessage("No auction selected"); return; }
            Auction a = manager.getAuction(aid);
            if (a == null) { p.sendMessage("Auction not found"); return; }

            double minIncrement = manager.getPlugin().getConfig().getDouble("anvil-bid-min-increment", 1.0);
            double required = Math.max(a.startingPrice, a.currentBid + minIncrement);
            if (bid < required) { p.sendMessage("Your bid must be at least " + required); return; }

            if (!manager.getEconomy().has(p, bid)) { p.sendMessage("You cannot afford this bid."); return; }

            manager.getEconomy().withdrawPlayer(p, bid);
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

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        awaitingAuction.remove(p);
        awaitingSearch.remove(p);
        awaitingPriceFilterMin.remove(p);
        awaitingPriceFilterMax.remove(p);
        awaitingAdminBid.remove(p);
    }
}
