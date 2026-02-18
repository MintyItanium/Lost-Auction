package com.betafish.auctionhouse;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class AuctionAdminCommand implements CommandExecutor, TabCompleter {
    private final AuctionManager manager;

    public AuctionAdminCommand(AuctionManager manager) { this.manager = manager; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players may use this command. If you think this is a error, please contact server staff, or if you are staff, make a bug report.");
            return true;
        }
        Player p = (Player) sender;
        if (!p.hasPermission("beta.auction.admin")) {
            p.sendMessage("You do not have permission to use this command.");
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("end")) {
            String id = args[1];
            boolean ok = manager.forceEnd(id);
            if (ok) p.sendMessage("[AuctionAdmin] Auction " + id + " was force-ended.");
            else p.sendMessage("[AuctionAdmin] Auction " + id + " not found.");
            return true;
        }
        if (args.length == 0) {
            AuctionGUI.openAdmin(p, manager);
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
        if (!player.hasPermission("beta.auction.admin")) {
            return completions;
        }

        if (args.length == 1) {
            // First argument completion
            String partial = args[0].toLowerCase();
            if ("end".startsWith(partial)) completions.add("end");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("end")) {
            // Second argument for "end" command - suggest auction IDs
            String partial = args[1].toLowerCase();
            for (String auctionId : manager.listAuctions().stream().map(a -> a.id).toList()) {
                if (auctionId.toLowerCase().startsWith(partial)) {
                    completions.add(auctionId);
                }
            }
        }

        return completions;
    }
}
