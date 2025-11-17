package fr.maxlego08.zauctionhouse.api.messages;

/**
 * An interface representing an essential message with a specific type.
 */
public interface AuctionMessage {

    /**
     * Retrieves the type of the message.
     *
     * @return the {@link MessageType} of the message
     */
    MessageType messageType();
}

