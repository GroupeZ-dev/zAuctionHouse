package fr.maxlego08.zauctionhouse.api.storage;

import fr.maxlego08.zauctionhouse.api.item.StorageType;

/**
 * Levee quand un UPDATE compare-and-set n'a modifie AUCUNE ligne : la ligne ne portait plus
 * l'etat source attendu au moment de l'ecriture.
 * <p>
 * Autrement dit, un autre serveur (ou un autre chemin local) a deja fait transiter cet item.
 * L'appelant a PERDU la course : il ne doit ni remettre l'item au joueur, ni deplacer d'argent,
 * ni restaurer un statut du cycle LISTED. Il doit converger vers la verite de la base, c'est-a-dire
 * purger sa copie memoire.
 */
public class StaleItemException extends RuntimeException {

    private final int itemId;
    private final StorageType expectedFrom;
    private final StorageType destination;

    /**
     * Creates a new stale item exception.
     *
     * @param itemId       identifier of the item whose row did not move
     * @param expectedFrom storage state the row was expected to still carry
     * @param destination  storage state the caller tried to move the row to
     */
    public StaleItemException(int itemId, StorageType expectedFrom, StorageType destination) {
        super("Item " + itemId + " is no longer " + expectedFrom + " (transition to " + destination + " matched 0 row)");
        this.itemId = itemId;
        this.expectedFrom = expectedFrom;
        this.destination = destination;
    }

    /**
     * Gets the identifier of the item that lost the race.
     *
     * @return the item identifier
     */
    public int getItemId() {
        return this.itemId;
    }

    /**
     * Gets the storage state the row was expected to still carry.
     *
     * @return the expected source storage state
     */
    public StorageType getExpectedFrom() {
        return this.expectedFrom;
    }

    /**
     * Gets the storage state the caller tried to move the row to.
     *
     * @return the destination storage state
     */
    public StorageType getDestination() {
        return this.destination;
    }

    /**
     * Deballe une chaine de CompletionException / ExecutionException et rend l'exception de
     * course perdue si elle s'y trouve, {@code null} sinon.
     *
     * @param throwable the throwable to unwrap, may be {@code null}
     * @return the stale exception, or {@code null} if the cause chain contains none
     */
    public static StaleItemException unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof StaleItemException stale) return stale;
            Throwable cause = current.getCause();
            current = (cause == current) ? null : cause;
        }
        return null;
    }
}
