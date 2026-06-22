# 4.0.0.9

- **Fixed** Sales history spamming `Item not found for log ID` warnings in console - logs migrated from V3 are created with `item_id = 0` (no direct mapping to V4 items), causing the history service to log a warning for every V3 entry. The history now filters out logs with invalid item references at the SQL level
- **Added** History configuration in `config.yml` - `history.max-entries` limits the number of history entries displayed per player (default: 500, 0 = unlimited), `history.expire-after-days` hides entries older than the specified number of days (default: 0 = never expire). Useful for servers with large transaction volumes to reduce database load and improve history loading times
- **Added** Admin log management commands - `/ah admin logs purge <days>` deletes all logs older than the specified number of days, `/ah admin logs player <player>` deletes all logs for a specific player, `/ah admin logs clear-migrated` deletes all V3 migrated logs (entries with `item_id = 0`). All operations run asynchronously and report the number of deleted entries
- **Fixed** Ghost items in `/ah selling` on multi-server (Redis) setups - when a player removed a selling item on server #1 and switched to server #2, the item could still appear with "being purchased" lore and could not be removed, blocking selling slots. Three fixes applied: (1) `ItemRemovedListener` now removes the item from in-memory storage immediately on message receipt instead of waiting for an async DB query, (2) `RemoveService` no longer restores the item status via Redis when the local removal already completed (preventing ghost items from reappearing as AVAILABLE on other servers after a cluster notification timeout), (3) `getPlayerSellingItems` now filters out items with `DELETED` status as defense-in-depth
- **Changed** Discord webhook `%expires_at%` placeholder now outputs a Discord [dynamic timestamp](https://discord.com/developers/docs/reference#message-formatting-timestamp-styles) (`<t:unix_seconds:f>`) instead of a pre-formatted date string. Discord renders it in each viewer's own local timezone (e.g. `12 June 2026 18:55`), making it easier to read and timezone-agnostic. Items with no expiration still fall back to the formatted date
- **Added** Per-item custom images for Discord webhooks - a new `custom-images` section in `discord.yml` lets you override the `%item_image_url%` for specific items. Each entry pairs a `rule` (same format as `categories.yml`/`rules.yml`: `material`, `name`, `lore`, `custom-model-data`, `oraxen`, `itemsadder`, `nexo`, `mmoitems`, etc.) with an image URL. The first matching rule wins; items matching no rule fall back to the material-based `item-image-url` pattern. Ideal for custom items whose generic material does not represent the real item. Dominant color extraction caches the color per image URL so custom items sharing a material no longer collide

# 4.0.0.8

- **Fixed** Critical item duplication in multi-server (Redis) setups - when a player removed a selling item via the selling inventory, the item was correctly given to the player and marked as `DELETED` in the database, but other servers received a generic `LISTED` removal message and incorrectly recreated the item in their `EXPIRED` cache, allowing it to be claimed a second time on another server
- **Fixed** `ItemRepository.select(int id)` not filtering `DELETED` items - single-item database queries returned items already marked as `DELETED`, while the bulk `select()` method correctly excluded them. This allowed the Redis `ItemRemovedListener` to reload deleted items and re-add them to cache
- **Added** `destinationStorageType` to cluster bridge `removeItem` method - the `ItemRemovedMessage` now includes the exact destination storage type (`DELETED` or `EXPIRED`) so other servers know exactly what happened instead of guessing. Backward compatible via a `default` method on `AuctionClusterBridge`
- **Fixed** `ItemRemovedListener` (Redis addon) rewritten with defense-in-depth - uses the explicit destination from the message first, falls back to database state verification, and never blindly assumes an item should go to `EXPIRED`
- **Fixed** `CreateOptionsMigration` failing on MySQL with `errno: 150 "Foreign key constraint is incorrectly formed"` - the `player_unique_id` column used `uuid()` type which could produce a different collation than the existing `players` table (especially on MySQL 8.0+ with changed default collation). Changed to `string(36)` to match all other foreign key columns referencing `players.unique_id`

# 4.0.0.7 

- **Added** `banned-rules` support for categories - allows excluding specific items from a category even if they match the inclusion rules. For example, a netherite hoe would normally appear in the "Tools" category, but if it has a specific CustomModelData (e.g., 300), it can be excluded using a banned rule. Uses the same rule types as regular rules (material, tag, lore, custom-model-data, etc.)
- **Added** Broadcast system - sends messages to all online players when items are listed or purchased. Configurable per event type (sell/purchase) with options to exclude the seller/buyer. Supports per-category message overrides using MiniMessage format with `%seller%`, `%buyer%`, `%items%`, `%price%`, `%category%` placeholders. Disabled by default, enable in `config.yml` under `broadcast`
- **Added** Player options system - extensible per-player preference system backed by the database. Players can toggle options via `/ah option` (opens GUI) or `/ah option <option_name> [value]` (command toggle). Options are cached in memory and only stored in the database when different from the default value. Currently supports `broadcast_sell` and `broadcast_purchase` to opt out of broadcast messages
- **Added** Options inventory (`/ah option`) - 27-slot GUI with toggle buttons for each broadcast option, showing current status (enabled/disabled)
- **Added** Admin option commands - `/ah admin option set <player> <option> <value>` to set options for a player, `/ah admin option list <player>` to view player options, `/ah admin option reset <player>` to reset all options to defaults
- **Added** `%zauctionhouse_option_<option_name>%` PlaceholderAPI placeholder - returns `true`/`false` for a player's option value (e.g., `%zauctionhouse_option_broadcast_sell%`)

# 4.0.0.6

- **Added** `ZAUCTIONHOUSE_COMBINED_ITEMS` button - combines selling, expired, and purchased items into a single paginated view. Each source is individually togglable via `include-selling`, `include-expired`, and `include-purchased`. Click actions automatically adapt to the item's storage type (cancel listing, claim expired, claim purchased). Each item type uses its own lore configuration from `config.yml`
- **Fixed** Item duplication exploit in multi-server (Redis) setups - removing a listing on one server and claiming the expired item could leave a ghost entry on the original server, allowing the item to be claimed again after natural expiration
- **Fixed** `ItemRemovedListener` (Redis addon) - removed fragile DB state validation that rejected removal messages due to race conditions, added safety-net cleanup across all storage types
- **Fixed** `ItemStatusListener` (Redis addon) - status change propagation now searches across all storage types (LISTED, EXPIRED, PURCHASED) instead of only LISTED
- **Fixed** `ExpireService` - added guard against re-expiring items already deleted/claimed on another server
- **Added** Redis Sentinel support (Redis addon) - enables high-availability Redis setups with automatic master discovery and failover. Configure `mode: "sentinel"` in `config.yml` with sentinel nodes. Fully backward compatible, existing standalone configurations work without changes
- **Fixed** `NullPointerException` when default economy is not configured - `/ah sell` and all sell-related buttons now display an error message instead of crashing. Added startup validation with prominent warnings in console when a default economy is missing from `economies.yml`
- **Fixed** Incorrect economy type names in `economies.yml` comments - `COINS_ENGINE` and `PLAYER_POINTS` did not match the actual CurrenciesAPI enum values (`COINSENGINE`, `PLAYERPOINTS`), causing these economy types to silently fail to load when users followed the documented names
- **Added** All permissions are now programmatically registered in Spigot on startup and reload. Includes static permissions (use, sell, admin, etc.), and dynamic permissions from configuration (listing limits, expiration tiers, tax bypass/reductions, economy access, inventory commands, cooldown bypass). All permissions are grouped under the `zauctionhouse.*` wildcard
- **Fixed** Items dropping on the ground when claiming expired, purchased, or selling items with a full inventory - items now stay in their storage when the player's inventory is full. Added `player-inventory-must-have-free-space` config option under `remove-expired-item` and `selling-item` sections (enabled by default)
- **Added** `ZAUCTIONHOUSE_REMOVE_ALL_EXPIRED`, `ZAUCTIONHOUSE_REMOVE_ALL_SELLING`, `ZAUCTIONHOUSE_REMOVE_ALL_PURCHASED` buttons - allows players to retrieve all items at once from expired, selling, and purchased inventories. Items are given one by one and stops when inventory is full (if `player-inventory-must-have-free-space` is enabled)
- **Added** `ItemContentProvider` API - extensible system for displaying the contents of container items (shulker boxes, custom containers from plugins). External plugins can register their own providers via `AuctionPlugin.getItemContentManager().registerProvider()`
- **Added** AxShulkers hook - displays the contents of shulker boxes managed by the AxShulkers plugin in the item content viewer. AxShulkers stores shulker contents externally instead of in vanilla NBT, so this hook is required to view their contents
- **Fixed** Seller not receiving money in multi-server (Redis) setups when the buyer is on a different server - the plugin tried to deposit money locally on the buyer's server where the seller's economy account may not exist. Money is now deferred to `PENDING` status in distributed environments and claimed by the seller on their own server via `/ah claim` or auto-claim on join
- **Added** `AuctionClusterBridge.isDistributed()` - allows cluster bridge implementations to signal a multi-server environment so the plugin can adapt its behavior (e.g., defer deposits instead of executing them locally)

# 4.0.0.5

- **Added** `ZAUCTIONHOUSE_CLAIM` button - displays pending money per economy with dynamic placeholders and allows players to claim directly from the auction GUI
- **Added** Configurable `loading-item` for the claim button, shown while pending money data is being fetched
- **Added** `PRICE_WITHOUT_DECIMAL` price format - displays prices without decimal places (e.g., `10000.50` -> `10000`)
- **Added** `%price-price-without-decimal%` placeholder - displays the price without decimals in item lore
- **Added** `/ah admin forceopen` can now be executed from the console
- **Added** `timezone` configuration option - allows changing the timezone used for all date placeholders (`%date%`, `%formatted-expire-date%`, `%expires_at%`). Supports all Java TimeZone IDs (e.g., `Europe/Paris`, `America/New_York`, `UTC`). Defaults to `auto` (server timezone)
- **Fixed** `/ah admin open` and `/ah admin history` tab completion no longer loads all offline players, preventing lag on servers with many players
- **Fixed** `updateListedItems` crash on Folia/Canvas - inventory holder access was running on an async thread instead of the main tick thread
- **Fixed** economy name argument configuration for the sell command - when the economy argument index was not configured, the default value was ignored causing a null economy name

### `ZAUCTIONHOUSE_CLAIM` button

Allows players to claim their pending money directly from the auction house inventory. The button displays per-economy pending amounts using dynamic placeholders and supports a loading state while data is being fetched.

**Available placeholders:**

| Placeholder | Description |
|-------------|-------------|
| `%pending_total%` | Total pending money across all economies (formatted) |
| `%pending_<economy_name>%` | Pending money for a specific economy (e.g., `%pending_vault%`) |
| `%has_pending%` | `true` or `false` |

The economy name corresponds to the name defined in your `economies.yml` configuration.

**Example configuration:**

```yaml
claim-money:
  type: ZAUCTIONHOUSE_CLAIM
  slot: 48
  loading-item:
    material: CLOCK
    name: "#2CCED2<bold>ᴄʟᴀɪᴍ ᴍᴏɴᴇʏ"
    lore:
      - "#8c8c8c• #ff3535Loading, please wait..."
  item:
    material: GOLD_INGOT
    name: "#2CCED2<bold>ᴄʟᴀɪᴍ ᴍᴏɴᴇʏ"
    lore:
      - ""
      - "#92ffffPending money: #2CCED2%pending_total%"
      - "#92ffffVault: #2CCED2%pending_vault%"
      - "#92ffffTokens: #2CCED2%pending_tokens%"
      - ""
      - "#8c8c8c• #2CCED2Click to claim your money"
```

# 4.0.0.4

- **Added** ZelAuction migration - migrate data from ZelAuction plugin to zAuctionHouse V4 using `/ah admin migrate zelauction confirm`
- **Added** Search system - players can search items by name, material, lore, or seller directly from the auction house GUI or via `/ah search <query>`
- **Added** `ZAUCTIONHOUSE_SEARCH` button - opens a chat-based search input with support for advanced filter operators
- **Added** `ZAUCTIONHOUSE_CLEAR_SEARCH` button - clears the active search filter, only visible when a search is active
- **Added** `/ah search <query>` command - search items directly from chat without opening the GUI first
- **Added** Advanced search filters with operators: `~` (contains), `=` (exact), `~=` (contains, ignore case), `==` (exact, ignore case)
- **Added** Searchable fields: `name`, `material`, `lore`, `seller` (e.g., `seller = Notch`, `name ~ Diamond`)
- **Added** `/ah admin forceopen <player> <inventory> [page]` - Open any inventory for a player at a specific page. Supports all inventory names (e.g., `auction`, `admin-selling-items`, `history`, `admin-logs`, etc.) with tab completion. Page defaults to 1
- **Added** `reset-search-on-open` config option - When enabled (default: `true`), the search filter is cleared every time a player opens the auction house, matching the existing `reset-category-on-open` behavior

### Search system

Players can search for items in the auction house using the search button or the `/ah search` command. The default search checks item name, material, lore, and seller name (case-insensitive substring).

**Advanced filters:**

```
field operator value
```

| Operator | Description |
|----------|-------------|
| `~` | Contains (case-sensitive) |
| `=` | Exact match (case-sensitive) |
| `~=` | Contains (ignore case) |
| `==` | Exact match (ignore case) |

| Field | Description |
|-------|-------------|
| `name` | Item display name |
| `material` | Item material type |
| `lore` | Item lore text |
| `seller` | Seller player name |

**Examples:**
```
seller = Notch
name ~ Diamond
material ~= sword
lore ~ Sharpness
```

# 4.0.0.3

- **Added** `ZAUCTIONHOUSE_SELL_LIMIT` button - displays remaining sell slots visually using a list of inventory slots, configurable per item type (auction, bid, rent)
- **Added** `%zauctionhouse_max_items_<type>%` placeholder - returns the maximum number of items a player can list for a specific type (auction, bid, rent)
- **Optimized** item lore placeholder resolution, placeholders are now pre-detected at config load and only resolved when referenced, significantly reducing CPU and memory usage per item render
- **Fixed** `/ah history` stuck on "Loading..." when `action.purchased-item.give-item: true` is enabled
- **Fixed** default config values
- **Fixed** default table prefix to `zauctionhousev4` (fixes compatibility with zAuctionHouse V3)

### `ZAUCTIONHOUSE_SELL_LIMIT` button

```yaml
sell-limit:
  type: ZAUCTIONHOUSE_SELL_LIMIT
  types:
    - auction
  slots:
    - 0-9
  item:
    material: LIME_STAINED_GLASS_PANE
    name: '&aAvailable slot'
```

### `%zauctionhouse_max_items_<type>%` placeholder

Returns the maximum number of items a player can list based on their permissions for the given type.

- `%zauctionhouse_max_items_auction%`
- `%zauctionhouse_max_items_bid%`
- `%zauctionhouse_max_items_rent%`

# 4.0.0.2

- **Added** `zauctionhouse_category` permissible for zMenu - allows conditional button visibility based on the player's currently selected category (defaults to `main`)
- **Added** `ZAUCTIONHOUSE_CATEGORY_SWITCHER` button - combines category cycling (left/right click) with dynamic lore showing enable/disable state per category
- **Added** `%zauctionhouse_category_id%` placeholder - returns the player's currently selected category ID
- **Added** `ZAUCTIONHOUSE_HISTORY_INVENTORY` button - opens the sales history inventory directly from any UI
- **Added** `force-amount-one` option in `config.yml` (`item-lore` section) - forces displayed item amount to 1 in the auction inventory while preserving the real amount internally
- **Changed** `AuctionEconomy` API - economy methods (`get`, `has`, `deposit`, `withdraw`) now accept `UUID` instead of `OfflinePlayer` for better compatibility
- **Updated** CurrenciesAPI dependency from 1.0.12 to 1.0.13
- **Fixed** category display name fallback - now uses the configured "all" category name instead of a hardcoded `"All"` string
- **Fixed** empty slot crash - list buttons (`ListedItems`, `ExpiredItems`, `PurchasedItems`, `SellingItems`) no longer crash when `emptySlot` is `-1` and the list is empty
- **Fixed** history loading slot - `HistoryItemsButton` now properly handles `loadingSlot` set to `-1` without throwing an error
- **Fixed** `LoadingSlotLoader` - added validation for invalid slot values with a clear error message

### `zauctionhouse_category` permissible

```yaml
requirements:
  - type: zauctionhouse_category
    category: "weapons"
```

### `ZAUCTIONHOUSE_CATEGORY_SWITCHER` button

```yaml
category-switcher:
  type: ZAUCTIONHOUSE_CATEGORY_SWITCHER
  slot: 49
  enable-text: "&a● %category%"
  disable-text: "&7○ %category%"
  categories:
    - "main"
    - "weapons"
    - "armor"
    - "tools"
    - "blocks"
    - "consumables"
    - "resources"
    - "enchanted-books"
    - "misc"
  item:
    material: COMPASS
    name: "&6Categories &7(&f%category%&7)"
    lore:
      - ""
      - "%main%"
      - "%weapons%"
      - "%armor%"
      - "%tools%"
      - "%blocks%"
      - "%consumables%"
      - "%resources%"
      - "%enchanted-books%"
      - "%misc%"
      - ""
      - "&7Left-click &8» &fNext"
      - "&7Right-click &8» &fPrevious"
```

### `force-amount-one` option

```yaml
item-lore:
  # Forces the displayed item amount to 1 in the auction inventory.
  # The real amount is preserved internally and given to the buyer on purchase.
  # Useful to keep a clean, uniform display.
  force-amount-one: false
```

# 4.0.0.1

- **Added** Thai as a supported language
- **Fixed** support for Minecraft **1.20.4**
- **Fixed** the `/zauctionhouse` command - it is no longer the default main command (this can be changed in `config.yml`)
- **Fixed** message system errors that could appear without reason
- **Added** the `reset-category-on-open` option, allowing categories to reset when reopening the inventory
- **Added** `EXCELLENTEECONOMY` economy support