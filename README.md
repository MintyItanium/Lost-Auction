# Auction House

a Auction House plugin for Paper 1.21-26.1.2

## Requirements

- Paper server 1.21-26.1.2 or ShreddedPaper 1.21.11-26.1.2
- VaultAPI
- EssentialsX
- Java 21+

## Features

### Core Auction System

- **Fixed Price Selling**: Sell items at a set price - instant purchase
- **Real Auctions**: Start with a minimum bid, accept higher bids until time expires
- **Auction History**: Track all your past auctions and bids, and search by item name

### Search & Filtering

- **Item Search**: Search auctions by item name
- **Price Filtering**: Filter by Price
- **Category Filtering**: Browse auctions by categories

### Configuration Options

- **Max Listings Per Player**: Prevent spam with configurable limits
- **Categories**: Fully configurable auction categories throught `categories.yml`

### Administrative Tools

- **Force End Auctions**: Admin command to end auctions early
- **View All History**: Admin access to complete auction history (WIP)
- **Logging**: debug logs written to `plugins/LostAuction/logs/debug-YYYY-MM-DD-HH.log` (one file per hour)

**Category Configuration:**

- `name`: The display name shown to players
- `item`: The Minecraft material used as the button icon
- Add/remove categories as needed
- Use any valid material name

## Usage

### Player Commands


| Command                             |                                               | Permission                 |
| ------------------------------------- | ----------------------------------------------- | ---------------------------- |
| `/auction`                          | Open the auction house GUI                    | `lost.auction`             |
| `/auction sell <price>`             | Put item in your hand up for fixed-price sale | `lost.auction`             |
| `/auction auction <starting_price>` | Put item in your hand up for a auction        | `lost.auction`             |
| `/auction history`                  | View your personal auction history            | `lost.auction`             |
| `/auction search`                   | Open search and filter interface              | `lost.auction`             |
| `/auction autoclaim`                | Toggle autoclaiming auctions                  | `lost.auction`             |
| `/auction refund`                   | Refunds your most recent purchase (Must be enabled in config)             | `lost.auction`             |
| `/auctionadmin`                     | Open admin panel for auctions                 | `lost.auction.admin`       |
| `/auction fullhistory`              | View all auction history                      | `lost.auction.fullhistory` |

## Permissions

- `lost.auction`: Use the auction house and most commands (default: all players)
- `lost.auction.admin`: Administrative commands for moderation staff (default: operators)
- `lost.auction.fullhistory`: View all auction history (default: operators)

## Building and Testing

Use **JDK 21** To build.

```bash
git clone https://github.com/MintyItanium/Lost-Auction.git
mvn clean package
```

Place the jar file in "/target" into your server `plugins/` folder, along with Vault and EssentialsX, then start the server.

## Credits 

Thanks to Caleb Graham and OJcream for assisting on this project, helping develop it, giving advice, and testing it.

Thanks to OGCraft.org's staff and its community for adopting the plugin so early, and all the valuable feedback and bug reports that have resulted from it.


#### Notes

a mirror of the source code can be found at: https://gitlab.com/MintyItanium/Lost-Auction

https://bstats.org/plugin/bukkit/Lost%20Auction/32291

