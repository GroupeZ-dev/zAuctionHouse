package fr.maxlego08.zauctionhouse.api.messages.messages;

import fr.maxlego08.zauctionhouse.api.messages.AuctionMessage;
import fr.maxlego08.zauctionhouse.api.messages.MessageType;

public record TitleMessage(String title, String subtitle, long start, long time, long end) implements AuctionMessage {

    @Override
    public MessageType messageType() {
        return MessageType.TITLE;
    }
}
