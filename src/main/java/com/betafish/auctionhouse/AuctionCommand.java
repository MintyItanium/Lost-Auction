package com.betafish.auctionhouse;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class AuctionCommand implements CommandExecutor, TabCompleter {
    private final AuctionManager manager;

    public AuctionCommand(AuctionManager m) { this.manager = m; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players may use this command, I'm a fish, not a magic fish.");
            return true;
        }
        Player p = (Player) sender;
        if (!p.hasPermission("beta.auction")) { 
            p.sendMessage("You do not have permission to use the auction house.");
            return true;
        }
        if (args.length == 0) {
            // open GUI
            AuctionGUI.openMain(p, manager);
            return true;
        }

        if (args.length == 1) {
            String subcommand = args[0].toLowerCase();
            if (subcommand.equals("history")) {
                AuctionGUI.openHistory(p, manager);
                return true;
            } else if (subcommand.equals("allhistory")) {
                if (!p.hasPermission("beta.auction.fullhistory")) {
                    p.sendMessage("You do not have permission to view all auction history.");
                    return true;
                }
                AuctionGUI.openAllHistory(p, manager);
                return true;
            }
        }

        if (args.length >= 2) {
            String mode = args[0].toLowerCase();
            double price;
            try { price = Double.parseDouble(args[1]); } catch (Exception e) { p.sendMessage("You can't give things away for free."); return true; }

            ItemStack inHand = p.getInventory().getItemInMainHand();
            if (inHand == null || inHand.getType().isAir()) { p.sendMessage("Hold an item in your main hand to list it."); return true; }

            long duration = manager.getPlugin().getConfig().getLong("default-duration-hours", 24) * 3600L * 1000L; // I'm not sure if this math works or not

            if (mode.equals("sell")) {
                // fixed price
                inHand = inHand.clone();
                p.getInventory().setItemInMainHand(null);
                try {
                    manager.createAuction(p.getUniqueId(), inHand, Auction.Type.FIXED, price, duration);
                    p.sendMessage("[Auction] Item listed for sale at " + price);
                } catch (IllegalStateException e) {
                    p.getInventory().setItemInMainHand(inHand); // return item
                    p.sendMessage("[Auction] " + e.getMessage());
                }
                return true;
            } else if (mode.equals("auction")) {
                inHand = inHand.clone();
                p.getInventory().setItemInMainHand(null);
                try {
                    manager.createAuction(p.getUniqueId(), inHand, Auction.Type.AUCTION, price, duration);
                    p.sendMessage("[Auction] Item listed for auction starting at " + price);
                } catch (IllegalStateException e) {
                    p.getInventory().setItemInMainHand(inHand); // return item
                    p.sendMessage("[Auction] " + e.getMessage());
                }
                return true;
            }
        }

        p.sendMessage("Usage: /auction OR /auction sell <price> OR /auction auction <startingPrice> OR /auction history OR /auction allhistory");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            // First argument completion
            String partial = args[0].toLowerCase();
            if ("sell".startsWith(partial)) completions.add("sell");
            if ("auction".startsWith(partial)) completions.add("auction");
            if ("history".startsWith(partial)) completions.add("history");
            if ("allhistory".startsWith(partial) && player.hasPermission("beta.auction.fullhistory")) {
                completions.add("allhistory");
            }
        }
        // For sell and auction commands, I want to see if it can suggest prices,
        // but that's too complex for me to do right now, so maybe in the future.

        return completions;
    }
}
