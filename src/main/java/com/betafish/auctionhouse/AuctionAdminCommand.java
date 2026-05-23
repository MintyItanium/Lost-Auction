package com.betafish.auctionhouse;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AuctionAdminCommand implements CommandExecutor, TabCompleter {
    private final AuctionManager manager;

    public AuctionAdminCommand(AuctionManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players may use this command. If you think this is a error, please contact the server staff.");
            return true;
        }
        Player p = (Player) sender;
        if (!p.hasPermission("lost.auction.admin")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("end")) {
            String id = args[1];
            boolean ok;
            try {
                ok = manager.forceEnd(id);
            } catch (Exception ex) {
                p.sendMessage("[AuctionAdmin] Error while ending auction: " + ex.getMessage());
                ex.printStackTrace();
                return true;
            }
            if (ok) p.sendMessage("[AuctionAdmin] Auction " + id + " was force-ended.");
            else p.sendMessage("[AuctionAdmin] Auction " + id + " not found.");
            return true;
        }
        if (args.length == 0) {
            try {
                AuctionGUI.openAdmin(p, manager);
            } catch (Exception ex) {
                p.sendMessage("[AuctionAdmin] Could not open admin GUI: " + ex.getMessage());
                ex.printStackTrace();
            }
            return true;
        }

        p.sendMessage("Usage: /auctionadmin end <auctionId>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("lost.auction.admin")) {
            return completions;
        }

        if (args.length == 1) {
            String partial = args[0] == null ? "" : args[0].toLowerCase();
            if ("end".startsWith(partial)) completions.add("end");
        } else if (args.length == 2 && args[0] != null && args[0].equalsIgnoreCase("end")) {
            String partial = args[1] == null ? "" : args[1].toLowerCase();
            Collection<Auction> auctions = manager.listAuctions();
            if (auctions == null || auctions.isEmpty()) return completions;
            for (Auction a : auctions) {
                if (a == null) continue;
                String auctionId = a.id;
                if (auctionId == null) continue;
                if (auctionId.toLowerCase().startsWith(partial)) {
                    completions.add(auctionId);
                }
            }
        }

        
        return Collections.unmodifiableList(completions);
    }
}