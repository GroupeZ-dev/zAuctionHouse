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