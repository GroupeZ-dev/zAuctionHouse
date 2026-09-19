package fr.maxlego08.zauctionhouse.api.storage;

public interface Tables {

    String PLAYERS = "%prefix%players";

    String ITEMS = "%prefix%items";
    String AUCTION_ITEMS = "%prefix%auction_items";
    String BID_ITEMS = "%prefix%bid_items";
    String RENT_ITEMS = "%prefix%rend_items";

    String LOGS = "%prefix%logs";

    String OPTIONS = "%prefix%options";

    String TRANSACTIONS = "%prefix%transactions";

    /**
     * Sentinelle d'idempotence des migrations de donnees.
     * <p>
     * Doit rester STRICTEMENT identique a {@code MigrationStateRepository.TABLE_NAME}, qui porte
     * sa propre copie de la constante pour rester compilable independamment de ce module.
     */
    String MIGRATION_STATE = "%prefix%migration_state";

}
