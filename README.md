# Auction House

Simple Auction House plugin for Paper 1.21.x

*This Plugin Was Made for a project, the project wasn't completed, but it works without the other plugins.*


## Requirements

- Paper server (1.21.x) or ShreddedPaper 1.21.11
- Vault plugin
- EssentialsX
- Java 17+

## Building and Testing
Use **JDK 17** To build.
```bash
mvn clean package
```

Place the `target/betafish-auction-0.0.2.jar` into your server `plugins/` folder, along with Vault and EssentialsX, then start the server.

## Features

### Core Auction System
- **Fixed Price Selling**: Sell items at a set price - instant purchase
- **Real Auctions**: Start with a minimum bid, accept higher bids until time expires
- **Auction History**: Track all your past auctions and bids

### Advanced Search & Filtering
- **Item Search**: Search auctions by item name or display name
- **Type Filtering**: Filter by Price (Coming Soon) or Auction types
- **Category Filtering**: Browse auctions by configurable categories
- **Real-time Results**: All filters work instantly with live auction data

### Configuration Options
- **Max Listings Per Player**: Prevent spam with configurable limits
- **Categories**: Fully configurable auction categories (add/remove/edit)
- **Auction Durations**: Set minimum and maximum auction times
- **Economy Integration**: Full Vault economy support

### Administrative Tools
- **Force End Auctions**: Admin command to end auctions early
- **View All History**: Admin access to complete auction history

## Configuration

The plugin uses two configuration files:

### config.yml
Contains general auction settings:
```yaml
# Maximum auctions a player can have active at once
max-listings-per-player: 5

# Auction duration settings (in minutes)
min-auction-duration: 5
max-auction-duration: 1440  # 24 hours

# Auction house settings
auction-house-name: "Auction House"
```

### categories.yml
Contains category definitions with customizable display items:
```yaml
categories:
  general:
    name: "General"
    item: "CHEST"
  tools:
    name: "Tools"
    item: "IRON_PICKAXE"
  weapons:
    name: "Weapons"
    item: "IRON_SWORD"
  armor:
    name: "Armor"
    item: "IRON_CHESTPLATE"
  blocks:
    name: "Blocks"
    item: "GRASS_BLOCK"
  food:
    name: "Food"
    item: "BREAD"
  potions:
    name: "Potions and Effects"
    item: "POTION"
  other:
    name: "Other"
    item: "BARRIER"
```

**Category Configuration:**
- `name`: The display name shown to players
- `item`: The Minecraft material used as the button icon
- Add/remove categories as needed
- Use any valid Minecraft material name

## Usage

### Player Commands
- `/auction` — Open the auction house GUI
- `/auction sell <price>` — Put item in main hand up for fixed-price sale
- `/auction auction <starting_price>` — Put item in main hand up for bid-based auction
- `/auction history` — View your personal auction history
- `/auction search` — Open search and filter interface

### Admin Commands
- `/auctionadmin end <auction_id>` — Force-end a specific auction
- `/auctionadmin history` — View complete auction history

### GUI Navigation
1. **Main Auction House**: Browse all active auctions
2. **Search & Filter**: Access advanced filtering options
   - Search by item name
   - Filter by auction type (Fixed Price/Auction)
   - Filter by category
3. **Your History**: View all auctions you've participated in
4. **List Item**: Select items from inventory to auction
   - Choose item from inventory
   - Select category for the auction
   - Choose listing type (Fixed Price or Auction)
   - Enter price/bid amount via chat input

## Permissions

- `beta.auction`: Use the auction house (default: all players)
- `beta.auction.admin`: Administrative commands (default: operators)
- `beta.auction.fullhistory`: View all auction history (default: operators)
