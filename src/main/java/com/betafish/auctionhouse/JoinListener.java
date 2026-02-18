package com.betafish.auctionhouse;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {
    private final AuctionManager manager;

    public JoinListener(AuctionManager manager) { this.manager = manager; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        manager.deliverPending(e.getPlayer());
    }
}
