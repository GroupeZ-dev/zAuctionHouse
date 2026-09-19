package fr.maxlego08.zauctionhouse.api.cluster;

import fr.maxlego08.zauctionhouse.api.item.Item;

import java.util.UUID;

/**
 * Represents a distributed lock token for cluster synchronization.
 * <p>
 * Un jeton est UNIQUE PAR ACQUISITION : il porte l'identite du detenteur et un nonce
 * aleatoire. C'est ce qui rend la verification de propriete du script Lua de
 * deverrouillage reellement discriminante entre serveurs — avant, tous les noeuds
 * produisaient litteralement la meme chaine "item:&lt;id&gt;" pour un item donne, et un
 * deverrouillage retardataire detruisait le verrou VIVANT d'un autre serveur.
 *
 * @param value the unique identifier for this lock
 */
public record LockToken(String value) {

    private static final String NOOP_VALUE = "NOOP";
    private static final String UNAVAILABLE_VALUE = "UNAVAILABLE";

    /**
     * Sentinelle « acquisition refusee par contention » : un autre acteur detient deja le
     * verrou. L'operation doit echouer proprement (LOCK_FAILED), pas remonter une erreur.
     *
     * @return a no-op lock token
     */
    public static LockToken noop() {
        return new LockToken(NOOP_VALUE);
    }

    /**
     * Sentinelle « item dans un etat terminal » : l'annonce est vendue, retiree ou detruite,
     * aucune acquisition n'est possible et ne le sera jamais. Distinguer ce cas de la simple
     * contention permet de remonter ITEM_NOT_AVAILABLE plutot que LOCK_FAILED.
     * <p>
     * Consommee a partir de {@link AuctionClusterBridge#checkAndLock(Item, UUID, fr.maxlego08.zauctionhouse.api.item.StorageType)}.
     *
     * @return an unavailable lock token
     */
    public static LockToken unavailable() {
        return new LockToken(UNAVAILABLE_VALUE);
    }

    /**
     * Emet un jeton unique pour cette acquisition, sans identite de detenteur.
     * <p>
     * Preferer {@link #issue(Item, UUID)}, qui inscrit en plus l'identite du detenteur dans
     * la valeur du jeton.
     *
     * @param auctionItem the item to create a lock token for
     * @return a lock token identifying this acquisition
     */
    public static LockToken issue(Item auctionItem) {
        return issue(auctionItem, null);
    }

    /**
     * Creates a lock token for the specified auction item.
     * <p>
     * CONSERVEE POUR COMPATIBILITE BINAIRE : la signature et le type de retour sont
     * inchanges, seul le corps a change. Tout addon compile contre une version anterieure
     * de cette API beneficie donc du jeton unique sans recompilation.
     *
     * @param auctionItem the item to create a lock token for
     * @return a lock token identifying this acquisition
     * @deprecated remplace par {@link #issue(Item, UUID)}, qui porte l'identite du detenteur.
     */
    @Deprecated
    public static LockToken of(Item auctionItem) {
        return issue(auctionItem, null);
    }

    /**
     * Emet un jeton unique pour cette acquisition precise.
     *
     * @param auctionItem the item to lock
     * @param ownerId     identite du detenteur (UUID d'instance de serveur, ou du joueur en
     *                    mono-serveur) ; {@code null} accepte
     * @return a lock token unique to this acquisition
     */
    public static LockToken issue(Item auctionItem, UUID ownerId) {
        return new LockToken("item:" + auctionItem.getId()
                + ':' + (ownerId == null ? "-" : ownerId)
                + ':' + UUID.randomUUID());
    }

    /**
     * @return {@code true} si ce jeton signale un echec d'acquisition par contention
     */
    public boolean isNoop() {
        return NOOP_VALUE.equals(this.value);
    }

    /**
     * @return {@code true} si ce jeton signale un item dans un etat terminal
     */
    public boolean isUnavailable() {
        return UNAVAILABLE_VALUE.equals(this.value);
    }

    /**
     * @return {@code true} si un verrou a reellement ete acquis (ni noop, ni unavailable)
     */
    public boolean isAcquired() {
        return !isNoop() && !isUnavailable();
    }
}
