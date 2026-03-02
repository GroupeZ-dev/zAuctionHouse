package fr.maxlego08.zauctionhouse.api.messages;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.messages.ClassicMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum Message {

    PREFIX("<primary>zAuctionHouse <secondary>• "),

    VERSION_AVAILABLE("<#ff0000>There is a new version of zAuctionHouse available ! <gray>(<white>current: <#ff9900>%version% <gray>| <white>latest: <#00ff00>%latest%<gray>)"),

    COMMAND_SYNTAX_ERROR("<error>You must execute the command like this<gray>: <success>%syntax%"),
    COMMAND_NO_PERMISSION("<error>You do not have permission to run this command."),
    COMMAND_NO_CONSOLE("<error>Only one player can execute this command."),
    COMMAND_NO_ARG("<error>Impossible to find the command with its arguments."),
    COMMAND_RESTRICTED("<error>You cannot use this command here."),
    COMMAND_SYNTAX_HELP("<white>%syntax% <dark_gray>» <gray>%description%"),

    INVENTORY_NOT_FOUND("<error>Impossible to find the inventory <white>%inventory-name%<error>."),

    COMMAND_DESCRIPTION_AUCTION("Open auction house"),
    COMMAND_DESCRIPTION_AUCTION_SELL("Add an item to the sale"),
    COMMAND_DESCRIPTION_AUCTION_RENT("Add an item for rent"),
    COMMAND_DESCRIPTION_AUCTION_BID("Add an item to the auction"),
    COMMAND_DESCRIPTION_AUCTION_RELOAD("Reload configurations files"),
    COMMAND_DESCRIPTION_AUCTION_ADMIN("Open administrative tools for auctions"),
    COMMAND_DESCRIPTION_AUCTION_ADMIN_GENERATE("Generate fake items"),
    COMMAND_DESCRIPTION_AUCTION_ADMIN_OPEN("Open current auction items"),
    COMMAND_DESCRIPTION_AUCTION_ADMIN_HISTORY("Open player auction history"),
    COMMAND_DESCRIPTION_AUCTION_ADMIN_ADD("Add an item to the auction"),

    COMMAND_DESCRIPTION_AUCTION_ADMIN_CACHE("Manage player cache"),
    COMMAND_DESCRIPTION_AUCTION_ADMIN_CACHE_SET("Set a value in player cache"),
    COMMAND_DESCRIPTION_AUCTION_ADMIN_CACHE_CLEAR("Clear player cache"),
    COMMAND_DESCRIPTION_AUCTION_ADMIN_CACHE_SHOW("Show player cache"),
    COMMAND_DESCRIPTION_AUCTION_CLAIM("Claim pending money from sales"),
    COMMAND_DESCRIPTION_AUCTION_PAGE("Open auction house at a specific page"),

    SELL_ERROR_AIR("<error>Are you stupid ? You can't sell air !"),
    SELL_ERROR_CHANGE("<error>The item in your hand has changed, sale cancelled."),
    SELL_ERROR_ECONOMY("<error>Unable to find the economy <white>%name%<error>."),
    SELL_INVENTORY_EMPTY("<error>You must place items in the inventory before confirming."),
    SELL_INVENTORY_CANCELLED("<error>You cancelled the sale, your items have been returned."),
    SELL_ITEMS_CLEARED("<success>Selected items have been cleared."),
    SELL_ITEM_ADDED("<success>Item added to sale list."),
    SELL_ITEM_REMOVED("<success>Item removed from sale list."),

    ADMIN_TARGET_REQUIRED("<error>You must specify a valid target player."),
    ADMIN_TARGET_NOT_FOUND("<error>Unable to find the player <white>%target%<error>."),
    ADMIN_OPEN_INVENTORY("<success>Opening %type% items for <white>%target%<success>."),
    ADMIN_OPEN_HISTORY("<success>Opening history for <white>%target%<success>."),
    ADMIN_ITEM_REMOVED("<success>You removed <white>%items%<success> from <white>%target%<success>."),
    ADMIN_ITEM_ADDED("<success>You added <white>%items%<success> to <white>%target%<success> in <white>%type%<success>."),
    ADMIN_ITEM_RETRIEVED("<success>Item(s) retrieved successfully."),
    ADMIN_NO_ITEM_TO_RETRIEVE("<error>No item to retrieve from this log entry."),

    RELOAD_SUCCESS("<success>You just reloaded the plugin !"),

    ITEM_REMOVE_LISTED("#e6fff3You just removed %items% #e6fff3from the listed items."),
    ITEM_REMOVE_EXPIRED("#e6fff3You just removed %items% #e6fff3from the expired items."),
    ITEM_REMOVE_PURCHASED("#e6fff3You just removed %items% #e6fff3from the purchased items."),
    ITEM_REMOVE_SELLING("#e6fff3You just removed %items% #e6fff3from your items."),

    ITEM_SOLD("#e6fff3You just sold %items% #e6fff3for #92bed8%price%#e6fff3."),

    ITEM_BOUGHT_SELLER("#ffacd5%buyer% #e6fff3just bought %items% #e6fff3for #92bed8%price%#e6fff3."),
    ITEM_BOUGHT_BUYER("#e6fff3You have just bought %items% #e6fff3for #92bed8%price%#e6fff3."),

    NOT_ENOUGH_MONEY("<error>You don’t have enough money to buy this."),
    NOT_ENOUGH_SPACE("<error>You don't have enough space in your inventory to buy this item."),

    PRICE_TOO_HIGH("<error>You cannot sell for more than <white>%max-price%<error>."),
    PRICE_TOO_LOW("<error>You cannot sell for less than <white>%min-price%<error>."),

    LISTED_ITEMS_LIMIT("<error>You cannot sell more than <white>%max-items%<error> items<error>. &8(&7Did you set the zauctionhouse.<number in config.yml> ?&8)"),
    WORLD_BANNED("<error>You cannot sell items in this world."),

    ITEM_BLACKLISTED("<error>You cannot sell blacklisted items."),
    ITEM_WHITELISTED("<error>You cannot sell an item that is not whitelist."),

    ADMIN_GENERATE_WARNING("<error>⚠ WARNING ⚠",
            "<error>This command will generate <white>%amount%<error> fake auction items for performance testing.",
            "<error>This data is <white>FAKE<error> and will need to be reset after testing.",
            "<error>Execute this command again within <white>30 seconds<error> to confirm."),
    ADMIN_GENERATE_CONFIRMED("<success>Generation confirmed! Creating <white>%amount%<success> auction items..."),
    ADMIN_GENERATE_PROGRESS("<gray>Progress: <white>%current%<gray>/<white>%total%<gray> items created..."),
    ADMIN_GENERATE_COMPLETE("<success>Successfully generated <white>%amount%<success> auction items in <white>%time%ms<success>."),
    ADMIN_GENERATE_NO_MATERIALS("<error>No valid materials found in categories. Make sure you have categories other than 'misc' configured."),
    ADMIN_GENERATE_INVALID_AMOUNT("<error>Invalid amount. Please specify a number between <white>1<error> and <white>100000<error>."),

    ADMIN_CACHE_SHOW_HEADER("<primary>Cache for <white>%player%<primary>:"),
    ADMIN_CACHE_SHOW_ENTRY("<gray>  %key% <dark_gray>» <white>%value%"),
    ADMIN_CACHE_SHOW_EMPTY("<gray>  No cache entries found."),
    ADMIN_CACHE_CLEARED("<success>Cleared cache key <white>%key%<success> for <white>%player%<success>."),
    ADMIN_CACHE_CLEARED_ALL("<success>Cleared all cache for <white>%player%<success>."),
    ADMIN_CACHE_SET("<success>Set <white>%key%<success> to <white>%value%<success> for <white>%player%<success>."),
    ADMIN_CACHE_INVALID_KEY("<error>Invalid cache key <white>%key%<error>. Use tab completion for valid keys."),
    ADMIN_CACHE_INVALID_VALUE("<error>Invalid value <white>%value%<error> for key <white>%key%<error>."),
    ADMIN_CACHE_KEY_NOT_SETTABLE("<error>Cache key <white>%key%<error> cannot be set from command."),
    ADMIN_CACHE_PLAYER_NOT_ONLINE("<error>Player <white>%player%<error> is not online."),

    CLAIM_NO_PENDING("<error>You have no pending money to claim."),
    CLAIM_SUCCESS("<success>You have successfully claimed your pending money!"),
    CLAIM_ECONOMY_SUCCESS("<success>You received <white>%amount%<success> from <white>%economy%<success>."),
    CLAIM_PENDING_NOTIFY("#e6fff3You have pending money to claim! Use <white>/ah claim<#e6fff3> to receive <white>%amount%<#e6fff3>."),

    SALES_NOTIFICATION("<click:run_command:/ah history>#e6fff3While you were away, <white>%count%<#e6fff3> of your items were sold for a total of <white>%total%<#e6fff3>!",
            "<click:run_command:/ah history>#8c8c8c• #2CCED2Click here<#92ffff> to view your sales history</click>"),

    // Tax messages
    TAX_SELL_APPLIED("<gray>A tax of <white>%tax%<gray> (%percentage%%) has been applied to this sale."),
    TAX_PURCHASE_APPLIED("<gray>A tax of <white>%tax%<gray> (%percentage%%) has been applied to this purchase."),
    TAX_CAPITALISM_INFO("<gray>The price includes <white>%tax%<gray> VAT (%percentage%%)."),
    TAX_EXEMPT("<green>You are exempt from taxes."),
    TAX_REDUCED("<green>You benefit from a reduced tax rate (%percentage%%)."),
    TAX_INSUFFICIENT_FUNDS("<error>You don't have enough money to pay the tax of <white>%tax%<error>.");

    private AuctionPlugin plugin;
    private List<AuctionMessage> messages = new ArrayList<>();

    Message(String message) {
        this(MessageType.TCHAT, message);
    }

    Message(MessageType messageType, String message) {
        this.messages.add(new ClassicMessage(messageType, Collections.singletonList(message)));
    }

    Message(String... message) {
        this(MessageType.TCHAT, message);
    }

    Message(MessageType messageType, String... messages) {
        this.messages.add(new ClassicMessage(messageType, Arrays.asList(messages)));
    }

    Message(AuctionMessage... AuctionMessages) {
        this.messages = Arrays.asList(AuctionMessages);
    }

    public static Message fromString(String string) {
        try {
            return valueOf(string);
        } catch (Exception ignored) {
            return null;
        }
    }

    public List<AuctionMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<AuctionMessage> messages) {
        this.messages = messages;
    }

    public String toConfigurationName() {
        return name().replace("_", "-").toLowerCase();
    }

    public String getMessageAsString() {
        String configurationName = this.toConfigurationName();
        if (this.messages.isEmpty()) {
            this.plugin.getLogger().severe(configurationName + " is empty ! Check your configuration");
            return "Error with " + configurationName + ", check your console";
        }
        AuctionMessage AuctionMessage = this.messages.getFirst();
        if (AuctionMessage instanceof ClassicMessage classicMessage) {

            if (classicMessage.messages().isEmpty()) {
                this.plugin.getLogger().severe(configurationName + " message is empty ! Check your configuration");
                return "Error with " + configurationName + ", check your console";
            }

            return classicMessage.messages().getFirst();
        }

        this.plugin.getLogger().severe(configurationName + " is not a tchat message ! Check your configuration");
        return "Error with " + configurationName + ", check your console";
    }

    public void setPlugin(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public List<String> getMessageAsStringList() {
        return this.messages.stream().filter(AuctionMessage -> AuctionMessage instanceof ClassicMessage).map(AuctionMessage -> (ClassicMessage) AuctionMessage).map(ClassicMessage::messages).flatMap(List::stream).toList();
    }
}