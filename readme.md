<p align="center">
  <img src="https://img.groupez.dev/zauctionhouse/banner.png" alt="zAuctionHouse Banner" width="800">
</p>

<h1 align="center">zAuctionHouse V4</h1>

<p align="center">
  <strong>The most powerful and customizable auction house plugin for Minecraft servers</strong>
</p>

<p align="center">
  <a href="https://www.spigotmc.org/resources/zauctionhouse-1-8-1-21.81494/"><img src="https://img.shields.io/badge/Spigot-1.8--1.21-orange.svg" alt="Spigot"></a>
  <a href="https://discord.groupez.dev/"><img src="https://img.shields.io/discord/music?label=Discord&logo=discord" alt="Discord"></a>
  <a href="https://zauctionhouse.groupez.dev/"><img src="https://img.shields.io/badge/Docs-Wiki-blue.svg" alt="Documentation"></a>
  <img src="https://img.shields.io/badge/Java-21-red.svg" alt="Java 21">
  <img src="https://img.shields.io/badge/License-Premium-green.svg" alt="License">
</p>

<p align="center">
  <a href="https://zauctionhouse.groupez.dev/">Documentation</a> •
  <a href="https://discord.groupez.dev/">Discord</a> •
  <a href="https://www.spigotmc.org/resources/zauctionhouse-1-8-1-21.81494/">Spigot</a>
</p>

---

## 📖 About zAuctionHouse

**zAuctionHouse** is a premium auction house plugin that allows players to buy and sell items on your Minecraft server. Built with performance, customization, and user experience in mind, it provides a complete trading solution for any server type - from small survival servers to large network infrastructures.

After nearly 5 years since the release of V3 (February 2021), **zAuctionHouse V4** represents a complete rewrite from the ground up, leveraging modern Java features, a new architecture, and years of community feedback to deliver the ultimate auction house experience.

### 🎯 Perfect For

- **Survival/SMP Servers** - Let players trade items naturally
- **Skyblock Servers** - Essential for island economies
- **Factions/Towny** - Facilitate inter-faction commerce
- **RPG Servers** - Custom item trading with MMOItems, Oraxen, etc.
- **Network Servers** - Multi-server synchronization with Redis addon

---

## ✨ Key Features

### 🏪 Core Auction System
- **Instant Buy Listings** - List items for a fixed price
- **Bulk Sales** - Sell multiple items in a single listing
- **Shulker Box Preview** - View contents before purchasing
- **Category Filtering** - Organize items by type (weapons, armor, tools, etc.)
- **Advanced Sorting** - Sort by price, date, name, and more
- **Pagination** - Handle thousands of listings efficiently

### 💰 Economy & Transactions
- **Multi-Economy Support** - Vault, PlayerPoints, CoinsEngine, Item-based, XP
- **Multiple Currencies** - Enable different economies simultaneously
- **Tax System** - Configurable taxes (sell, purchase, VAT/capitalism style)
- **Permission-based Tax Reductions** - VIP discounts on taxes
- **Auto-Claim** - Automatic money delivery or manual claiming
- **Offline Sales Notifications** - Know what sold while you were away

### 🎨 Complete Customization
- **zMenu Integration** - Fully customizable GUI via YAML
- **MiniMessage Support** - Modern text formatting with gradients, hex colors
- **Custom Lore Templates** - Define exactly how items appear
- **Pattern System** - Reusable GUI components
- **Placeholder Support** - PlaceholderAPI integration

### 🔧 Administration
- **Admin Panel** - View/manage any player's items
- **Action Logs** - Complete audit trail of all transactions
- **Transaction History** - Detailed financial records
- **Item Recovery** - Retrieve items from logs
- **Cache Management** - Admin commands for cache control
- **Performance Debugging** - Built-in profiling tools

### 🌐 Multi-Server Support
- **Redis Addon** - Synchronize across multiple servers
- **Distributed Locking** - Safe concurrent transactions
- **Real-time Updates** - Instant synchronization via pub/sub
- **Server Identification** - Track item origin across network

### 🔒 Security & Protection
- **Blacklist System** - Block specific items from sale
- **Whitelist System** - Only allow specific items
- **World Restrictions** - Disable selling in certain worlds
- **Listing Limits** - Permission-based item limits
- **Price Limits** - Min/max price per economy
- **Transaction Locking** - Prevent race conditions

### 📊 Discord Integration
- **Webhook Notifications** - Post sales to Discord
- **Rich Embeds** - Beautiful sale announcements
- **Dynamic Colors** - Extract item colors for embeds
- **Customizable Messages** - Full control over webhook content

---

## 🔌 Supported Plugins

### Economy Plugins
| Plugin | Status |
|--------|--------|
| Vault (EssentialsX, CMI, etc.) | ✅ Full Support |
| PlayerPoints | ✅ Full Support |
| CoinsEngine | ✅ Full Support |
| Experience/Levels | ✅ Full Support |
| Item Currency | ✅ Full Support |

### Custom Item Plugins
| Plugin | Status |
|--------|--------|
| ItemsAdder | ✅ Full Support |
| Oraxen | ✅ Full Support |
| Nexo | ✅ Full Support |
| MMOItems | ✅ Full Support |
| ExecutableItems | ✅ Full Support |
| EcoItems | ✅ Full Support |
| MythicCrucible | ✅ Full Support |
| Slimefun | ✅ Full Support |
| HeadDatabase | ✅ Full Support |
| Nova | ✅ Full Support |
| Denizen | ✅ Full Support |
| AdvancedItems | ✅ Full Support |
| CustomCrafting | ✅ Full Support |
| zHead | ✅ Full Support |
| MagicCosmetics | ✅ Full Support |
| HMCCosmetics | ✅ Full Support |
| zItems | ✅ Full Support |
| CraftEngine | ✅ Full Support |
| ExecutableBlocks | ✅ Full Support |

### Required Dependencies
| Plugin | Purpose |
|--------|---------|
| [zMenu](https://www.spigotmc.org/resources/zmenu.110402/) | Inventory GUI Framework |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | Placeholder Support |

### Optional Dependencies
| Plugin | Purpose |
|--------|---------|
| [LuckPerms](https://luckperms.net/) | Offline permission checks |
| zAuctionHouse Redis | Multi-server synchronization |

---

## 🆚 V3 vs V4: What's New?

### Architecture Overhaul

| Aspect | V3 | V4 |
|--------|----|----|
| **Java Version** | Java 8 | Java 21 |
| **Build System** | Maven | Gradle |
| **Async Operations** | Callbacks | CompletableFuture |
| **Database** | Custom SQL/JSON | Sarah ORM (Repository Pattern) |
| **GUI System** | Built-in | zMenu (external, more powerful) |
| **Scheduler** | Bukkit | FoliaLib (Folia compatible) |

### New in V4

| Feature | Description |
|---------|-------------|
| **🚀 Better Performance** | Optimized caching, sorted item cache, lazy loading |
| **📦 Bulk Sales** | Sell multiple items in one listing |
| **🎁 Shulker Preview** | View shulker contents before buying |
| **💱 Multiple Economies** | Use different currencies simultaneously |
| **📜 Sales History** | Complete history of your sales |
| **🔔 Offline Notifications** | Know what sold while you were away |
| **💸 Advanced Tax System** | Per-item taxes, VAT style, reductions |
| **📊 Discord Webhooks** | Rich embed notifications |
| **🔧 Admin Tools** | Complete admin panel with logs |
| **🌐 Better Multi-Server** | Improved Redis addon with pub/sub |
| **⚙️ Performance Debug** | Built-in profiling for large servers |
| **🎨 MiniMessage** | Modern text formatting |

### Migration Path

V4 includes a built-in migration system to import your V3 data:
```
/ah admin migrate zauctionhousev3 confirm
```

Supports migration from:
- V3 SQLite database
- V3 MySQL/MariaDB database
- V3 JSON storage

---

## 🏆 Why Choose zAuctionHouse?

### vs. Other Auction Plugins

| Feature | zAuctionHouse V4 | Competitors |
|---------|------------------|-------------|
| **GUI Customization** | 100% via YAML (zMenu) | Limited or hardcoded |
| **Custom Item Support** | 20+ plugins | Few or none |
| **Multi-Economy** | Yes, simultaneous | Usually single economy |
| **Bulk Sales** | Yes | Rare |
| **Shulker Preview** | Yes | Rare |
| **Tax System** | Advanced (per-item, VAT) | Basic or none |
| **Multi-Server** | Redis addon | Rare |
| **Performance** | Optimized caching | Varies |
| **Modern Java** | Java 21 | Often outdated |
| **Active Development** | Yes | Varies |
| **Documentation** | Comprehensive | Varies |
| **Support** | Discord + Wiki | Varies |

### What Sets Us Apart

1. **🎯 True Customization**
   - Every GUI element is configurable via YAML
   - No hardcoded messages or interfaces
   - Pattern system for reusable components

2. **⚡ Performance First**
   - Sorted item cache for instant browsing
   - Async database operations
   - Optimized for servers with 10,000+ listings

3. **🔧 Developer Friendly**
   - Clean API for integration
   - Events for all major actions
   - Repository pattern for data access

4. **🛡️ Battle Tested**
   - Used on major networks
   - 5+ years of development experience
   - Active community feedback

5. **📚 Excellent Documentation**
   - Complete wiki with examples
   - Configuration file comments
   - Active Discord support

---

## 📥 Installation

1. Download [zMenu](https://www.spigotmc.org/resources/zmenu.110402/) and [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
2. Download zAuctionHouse from [Spigot](https://www.spigotmc.org/resources/zauctionhouse-1-8-1-21.81494/)
3. Place all JARs in your `plugins` folder
4. Restart your server
5. Configure in `plugins/zAuctionHouse/config.yml`
6. Customize GUIs in `plugins/zAuctionHouse/inventories/`

---

## 📝 Quick Start

### Basic Commands

| Command | Description |
|---------|-------------|
| `/ah` | Open the auction house |
| `/ah sell <price>` | Sell the item in your hand |
| `/ah sell <price> <amount>` | Sell a specific amount |
| `/ah selling` | View your listed items |
| `/ah expired` | View your expired items |
| `/ah purchased` | View items you bought |
| `/ah history` | View your sales history |
| `/ah claim` | Claim pending money |

### Admin Commands

| Command | Description |
|---------|-------------|
| `/ah reload` | Reload configuration |
| `/ah admin history <player>` | View player's history |
| `/ah admin open <type> <player>` | View player's items |
| `/ah admin migrate <source>` | Migrate from V3 |

---

## 🔗 Links

- 📖 **Documentation**: [zauctionhouse.groupez.dev](https://zauctionhouse.groupez.dev/)
- 💬 **Discord**: [discord.groupez.dev](https://discord.groupez.dev/)
- 🛒 **Spigot**: [spigotmc.org/resources/zauctionhouse](https://www.spigotmc.org/resources/zauctionhouse-1-8-1-21.81494/)
- 🐛 **Issues**: [GitHub Issues](https://github.com/Starter-zMenu/zAuctionHouseV4/issues)

---

## 💝 Support the Development

zAuctionHouse is a premium plugin developed with passion. Your purchase supports:
- Continuous development and new features
- Bug fixes and compatibility updates
- Documentation and support
- Server infrastructure

---

## 📜 License

zAuctionHouse is a premium plugin. Purchase includes:
- Lifetime updates
- Discord support
- Access to all addons

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/Maxlego08">Maxlego08</a>
</p>

---

# 🛠️ Development Roadmap

## New Features (Planned)

- [ ] YAML file update system improvements
- [ ] Message enable/disable options
- [ ] Sell inventory improvements with zTextGenerator
- [ ] Admin item sales system
- [ ] Favorite items system
- [x] Offline sales notification with history
- [x] Page command `/ah page <page>`
- [ ] Sale removal reason tracking
- [ ] Custom model data for item images
- [ ] Transaction state modification
- [ ] Sell items on behalf of players
- [ ] Custom bulk sale naming
- [ ] Inventory button validation
- [x] Renamed "owned" to "selling"
- [ ] More admin commands for item management
- [ ] Slot-based multi-item sales
- [ ] Custom Discord image URLs

## Missing V3 Features

### Commands
- [ ] `/ah search <string>` - Item search
- [ ] `/ah blacklist` - Player blacklist management
- [ ] `/ah config` - In-game configuration
- [ ] `/ah version` - Version display
- [ ] `/ah convert` - V2/PlayerAuctions conversion
- [ ] `/ah purge <days>` - Old transaction purge

### Systems
- [x] Tax system (global, per-item, bypass permissions)
- [ ] Priority system (VIP sorting)
- [ ] Per-item price limits
- [ ] Advanced search (chat, GUI, filters)
- [ ] Additional sort options (10 more)
- [ ] Cooldown system
- [ ] Player blacklist
- [ ] Global announcements
- [ ] Bid/auction system
- [ ] Rent system
- [ ] Dupe prevention (NMS/PDC)

### Integrations
- [ ] Citizens NPC support
- [ ] ProtocolLib advanced search
- [ ] ZEssentials mailbox

---

## zAuctionHouse Discord Bot (Planned)

- [ ] Private message notifications for sales
- [ ] Listing notifications
- [ ] Item filters for notifications
- [ ] Direct purchase from Discord

----

  ---
🟠 ÉLEVÉ (Devrait être corrigé)

1. Incohérence d'héritage des services
- PurchaseService extends AuctionService
- SellService extends ZUtils ← Incohérent!
- ExpireService n'extend pas AuctionService

2. Nommage incohérent des champs dans les services
- SellService : manager
- PurchaseService : plugin
- ExpireService : auctionManager

3. Gestion silencieuse des exceptions
- Metrics.java:132,150,315,317 - exceptions ignorées
- Repository.java - printStackTrace() au lieu de logging

4. Synchronisation mixte
- ConcurrentHashMap pour certains caches
- EnumMap (non thread-safe) pour storageItemsById

  ---
🟡 MOYEN (Améliorations)

1. Mélange français/anglais dans les commentaires
- ZAuctionManager.java:485,529
- ExpireService.java:39,43
- SellService.java:259,260

2. TODOs non implémentés
- ZEconomyManager.java:318
- ExpireService.java:78
- ListedItemsButton.java:103

3. SellService viole le Single Responsibility Principle
- Gère: vente, UI, validation, taxes, Discord

4. Algorithme O(n²) dans ZAuctionItem.java:84-98
- Fusion d'items similaires avec boucles imbriquées

  ---
🟢 BAS (Style de code)

1. Nommage des champs de mapping
   idsListedByOwner    // "ids" déjà pluriel + "Ids" en suffix
   idsExpiredByOwner
   idsPurchasedByBuyer
   Suggestion: itemIdsByOwner, expiredIdsByOwner, etc.

  ---
Voulez-vous que je corrige certains de ces problèmes? Je recommande de commencer par :
1. Renommer le package repositeries → repositories
2. Corriger le NPE dans SortedItemsCache
3. Corriger la fuite de ressources dans ZAuctionPlugin