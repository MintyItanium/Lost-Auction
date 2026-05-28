package com.betafish.auctionhouse;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Salmon;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class AuctionCommand implements CommandExecutor, TabCompleter {
    private final AuctionManager manager;

    MiniMessage mm = MiniMessage.miniMessage();
    private final Random random = new Random();
    private final Map<UUID, Long> easterEggCooldown = new HashMap<>();

    public AuctionCommand(AuctionManager m) { this.manager = m; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(mm.deserialize("<red>Only players may use this command."));
            return true;
        }
        Player p = (Player) sender;
        if (!p.hasPermission("lost.auction")) {
            p.sendMessage(mm.deserialize("<red>You do not have permission to use this command"));
            return true;
        }
        if (args.length == 0) {
            // open GUI
            AuctionGUI.openMain(p, manager);
            return true;
        }

        if (args.length >= 1) {
            String subcommand = args[0].toLowerCase();
            if (subcommand.equals("history")) {
                if (args.length >= 2) {
                    if (!p.hasPermission("lost.auction.fullhistory")) {
                        p.sendMessage(mm.deserialize("<red>You do not have permission to view other players' history"));
                        return true;
                    }
                    String targetName = args[1];
                    org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                    if (target.getName() == null) {
                        p.sendMessage(mm.deserialize("<red>Player not found."));
                        return true;
                    }
                    AuctionGUI.openHistory(p, manager, 0, target.getUniqueId());
                } else {
                    AuctionGUI.openHistory(p, manager, 0, null);
                }
                return true;
            } else if (subcommand.equals("allhistory") || subcommand.equals("fullhistory")) {
                if (!p.hasPermission("lost.auction.fullhistory")) {
                    p.sendMessage(mm.deserialize("<red>You do not have permission to use this command"));
                    return true;
                }
                AuctionGUI.openAllHistory(p, manager, 0);
                return true;
            } else if (subcommand.equals("autoclaim")) {
                boolean enabled = manager.toggleAutoClaim(p.getUniqueId());
                if (enabled) {
                    p.sendMessage(mm.deserialize("<green>[Auction] Auto-claim enabled. Expired items will be auto-delivered <yellow> when your inventory has space."));
                } else {
                    p.sendMessage(mm.deserialize("<yellow>[Auction] Auto-claim disabled. Expired items go to Unclaimed Items."));
                }
                return true;
            }
        }

        if (args.length >= 2) {
            String mode = args[0].toLowerCase();

            if (args[1].equalsIgnoreCase("fish") && mode.equals("sell")) {
                if (manager.getPlugin().getConfig().getBoolean("easter-egg-enabled", true)) {
                    UUID uid = p.getUniqueId();
                    long now = System.currentTimeMillis();
                    long last = easterEggCooldown.getOrDefault(uid, 0L);
                    if (!manager.getPlugin().getConfig().getBoolean("bypass-easter-egg-timer", false) && now - last < 300000) {
                        long remaining = (300000 - (now - last)) / 1000;
                        p.sendMessage(mm.deserialize("<red>You must wait " + remaining + " seconds before using this again."));
                        return true;
                    }
                    easterEggCooldown.put(uid, now);
                    fishcurse(p);
                    return true;
                }
            }

            double price;
            try { price = Double.parseDouble(args[1]); } catch (Exception e) { p.sendMessage(mm.deserialize("<red>You can't give things away for free.")); return true; }

            ItemStack inHand = p.getInventory().getItemInMainHand();
            if (inHand == null || inHand.getType().isAir()) { p.sendMessage(mm.deserialize("<green>Hold an item in your main hand to list it.")); return true; }

            long duration = manager.getPlugin().getConfig().getLong("default-duration-hours", 24) * 3600L * 1000L;

            if (mode.equals("sell")) {
                // buy it now
                inHand = inHand.clone();
                p.getInventory().setItemInMainHand(null);
                try {
                    manager.createAuction(p.getUniqueId(), inHand, Auction.Type.FIXED, price, duration);
                    p.sendMessage(mm.deserialize("<green>[Auction] <yellow>Item listed for sale at <green>" + price));
                } catch (IllegalStateException e) {
                    p.getInventory().setItemInMainHand(inHand); // return item
                    p.sendMessage(mm.deserialize("<red>[Auction] " + e.getMessage()));
                }
                return true;
            } else if (mode.equals("auction")) {
                inHand = inHand.clone();
                p.getInventory().setItemInMainHand(null);
                try {
                    manager.createAuction(p.getUniqueId(), inHand, Auction.Type.AUCTION, price, duration);
                    p.sendMessage(mm.deserialize("<green>[Auction] <yellow>Item listed for auction starting at <green>" + price));
                } catch (IllegalStateException e) {
                    p.getInventory().setItemInMainHand(inHand); // return item
                    p.sendMessage(mm.deserialize("<red>[Auction] " + e.getMessage()));
                }
                return true;
            }
        }

        p.sendMessage(mm.deserialize("<yellow>Usage: <green>/auction <white>OR <green>/auction sell <price><white> OR <green>/auction auction <startingPrice><white> OR <green>/auction history <white>OR <green>/auction fullhistory <white>OR <green>/auction autoclaim"));
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
            if ("autoclaim".startsWith(partial)) completions.add("autoclaim");
            if ("fullhistory".startsWith(partial) && player.hasPermission("lost.auction.fullhistory")) {
                completions.add("fullhistory");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("history") && player.hasPermission("lost.auction.fullhistory")) {
            String partial = args[1].toLowerCase();
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                String name = onlinePlayer.getName();
                if (name != null && name.toLowerCase().startsWith(partial)) {
                    completions.add(name);
                }
            }
        }

        return completions;
    }

    private void fishcurse(Player p) {
        p.sendMessage(mm.deserialize("<red>May the fishes have mercy on you."));
        org.bukkit.Location loc = p.getLocation();
        List<Salmon> salmons = new ArrayList<>();
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(manager.getPlugin(), () -> {
            for (int i = 0; i < 3; i++) {
                double xOffset = (random.nextDouble() - 0.5) * 16;
                double zOffset = (random.nextDouble() - 0.5) * 16;
                double yOffset = 15 + random.nextDouble() * 10;
                org.bukkit.Location spawnLoc = loc.clone().add(xOffset, yOffset, zOffset);
                Salmon salmon = loc.getWorld().spawn(spawnLoc, Salmon.class);
                salmon.setInvulnerable(true);
                salmon.setRemoveWhenFarAway(false);
                salmon.setSilent(true);
                salmons.add(salmon);
            }
        }, 0L, 15L);
        Bukkit.getScheduler().runTaskLater(manager.getPlugin(), () -> {
            Bukkit.getScheduler().cancelTask(taskId);
            for (Salmon salmon : salmons) {
                salmon.remove();
            }
        }, 300L);
    }
}
