package com.betafish.auctionhouse;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Auction implements ConfigurationSerializable {
    public enum Type {FIXED, AUCTION}

    public String id;
    public UUID seller;
    public ItemStack item;
    public Type type;
    public double startingPrice;
    public double currentBid;
    public UUID currentBidder;
    public long endTime; // epoch millis
    public String category;

    public Auction() {}

    public Auction(String id, UUID seller, ItemStack item, Type type, double startingPrice, long endTime) {
        this.id = id;
        this.seller = seller;
        this.item = item;
        this.type = type;
        this.startingPrice = startingPrice;
        this.currentBid = 0.0;
        this.currentBidder = null;
        this.endTime = endTime;
        this.category = "General"; // default category
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("seller", seller.toString());
        m.put("item", item);
        m.put("type", type.name());
        m.put("startingPrice", startingPrice);
        m.put("currentBid", currentBid);
        m.put("currentBidder", currentBidder == null ? null : currentBidder.toString());
        m.put("endTime", endTime);
        m.put("category", category);
        return m;
    }

    public static Auction deserialize(Map<String, Object> args) {
        Auction a = new Auction();
        a.id = (String) args.get("id");
        a.seller = UUID.fromString((String) args.get("seller"));
        a.item = (ItemStack) args.get("item");
        a.type = Type.valueOf((String) args.get("type"));
        a.startingPrice = ((Number) args.get("startingPrice")).doubleValue();
        a.currentBid = args.get("currentBid") == null ? 0.0 : ((Number) args.get("currentBid")).doubleValue();
        a.currentBidder = args.get("currentBidder") == null ? null : UUID.fromString((String) args.get("currentBidder"));
        a.endTime = ((Number) args.get("endTime")).longValue();
        a.category = (String) args.getOrDefault("category", "General");
        return a;
    }
}
