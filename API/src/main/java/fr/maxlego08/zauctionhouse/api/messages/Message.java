package fr.maxlego08.zauctionhouse.api.messages;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.messages.ClassicMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum Message {

    PREFIX("<primary>zAuctionHouse <secondary>• "),

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

    SELL_ERROR_AIR("<error>Are you stupid ? You can’t sell air !"),
    SELL_ERROR_ECONOMY("<error>Unable to find the economy <white>%name%<error>."),

    ADMIN_TARGET_REQUIRED("<error>You must specify a valid target player."),
    ADMIN_TARGET_NOT_FOUND("<error>Unable to find the player <white>%target%<error>."),
    ADMIN_OPEN_INVENTORY("<success>Opening %type% items for <white>%target%<success>."),
    ADMIN_ITEM_REMOVED("<success>You removed <white>%items%<success> from <white>%target%<success>."),
    ADMIN_ITEM_ADDED("<success>You added <white>%items%<success> to <white>%target%<success> in <white>%type%<success>."),

    RELOAD_SUCCESS("<success>You just reloaded the plugin !"),

    ITEM_REMOVE_LISTED("#e6fff3You just removed %items% #e6fff3from the listed items."),
    ITEM_REMOVE_EXPIRED("#e6fff3You just removed %items% #e6fff3from the expired items."),
    ITEM_REMOVE_PURCHASED("#e6fff3You just removed %items% #e6fff3from the purchased items."),
    ITEM_REMOVE_OWNED("#e6fff3You just removed %items% #e6fff3from your items."),

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
    ITEM_WHITELISTED("<error>You cannot sell an item that is not whitelist.");

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