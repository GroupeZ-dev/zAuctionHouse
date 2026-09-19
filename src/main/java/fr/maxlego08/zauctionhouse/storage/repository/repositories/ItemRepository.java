package fr.maxlego08.zauctionhouse.storage.repository.repositories;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.sarah.SchemaBuilder;
import fr.maxlego08.sarah.database.Schema;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.storage.Repository;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.ItemDTO;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ItemRepository extends Repository {

    /** Taille de lot : borne la clause IN de la relecture et la duree du verrou SQLite. */
    private static final int BATCH_SIZE = 500;

    /**
     * {@code pending_publish = 1} : annonce RESERVEE, le vendeur a ENCORE ses items.
     * <p>
     * Une ligne dans cet etat n'a rien coute au vendeur : elle peut etre supprimee sans aucune
     * compensation, et elle ne doit JAMAIS lui etre rendue - ce serait un second exemplaire.
     */
    public static final int PENDING_RESERVED = 1;

    /**
     * {@code pending_publish = 2} : annonce ENGAGEE, les items ont QUITTE l'inventaire du vendeur
     * et la publication n'est pas encore confirmee.
     * <p>
     * Une ligne dans cet etat represente un lot que le vendeur a PERDU : elle doit rester
     * reclamable, jamais etre supprimee.
     */
    public static final int PENDING_COMMITTED = 2;

    /** {@code pending_publish = 0} : cycle de vie normal, aucune reservation en cours. */
    private static final int PENDING_NONE = 0;

    /**
     * Bilan d'un balayage des reservations orphelines.
     *
     * @param recovered nombre de reservations ENGAGEES rendues reclamables a leur vendeur
     * @param purged    identifiants des reservations NON engagees supprimees (le vendeur avait
     *                  garde son lot : les lui rendre l'aurait duplique)
     */
    public record OrphanReservationSweep(int recovered, List<Integer> purged) {
    }

    public ItemRepository(AuctionPlugin plugin, DatabaseConnection connection) {
        super(plugin, connection, Tables.ITEMS);
    }

    public int create(Player seller, ItemType itemType, BigDecimal price, long expiredAt, AuctionEconomy auctionEconomy) {
        return create(seller.getUniqueId(), itemType, price, expiredAt, auctionEconomy);
    }

    public int create(UUID sellerUniqueId, ItemType itemType, BigDecimal price, long expiredAt, AuctionEconomy auctionEconomy) {
        return create(sellerUniqueId, itemType, price, expiredAt, auctionEconomy, StorageType.LISTED);
    }

    /**
     * Cree la ligne parente d'une annonce dans l'etat demande.
     * <p>
     * Avec {@link StorageType#DELETED} la ligne est RESERVEE : elle existe, elle porte deja son
     * vendeur, son prix et son economie, mais elle est invisible de tous les serveurs
     * ({@link #select()} et {@link #select(int)} filtrent DELETED). Elle ne devient une annonce
     * qu'apres {@link #publishListing(int)}, une fois son contenu insere.
     * <p>
     * Elle nait a {@link #PENDING_RESERVED} : a cet instant le vendeur a ENCORE son lot en main.
     *
     * @param sellerUniqueId     seller unique id
     * @param itemType           listing type
     * @param price              listing price
     * @param expiredAt          expiration timestamp in milliseconds
     * @param auctionEconomy     economy used by the listing
     * @param initialStorageType initial storage bucket; DELETED means "reserved, not published"
     * @return the generated row identifier
     */
    public int create(UUID sellerUniqueId, ItemType itemType, BigDecimal price, long expiredAt, AuctionEconomy auctionEconomy, StorageType initialStorageType) {
        var expiredAtDate = new Date(expiredAt);
        var serverName = this.plugin.getConfiguration().getServerName();
        boolean reserved = initialStorageType == StorageType.DELETED;
        return insertSync(schema -> {
            schema.string("item_type", itemType.name());
            schema.uuid("seller_unique_id", sellerUniqueId);
            schema.string("economy_name", auctionEconomy.getName());
            schema.decimal("price", price);
            schema.object("expired_at", expiredAtDate);
            schema.object("storage_type", initialStorageType.name());
            schema.object("pending_publish", reserved ? PENDING_RESERVED : PENDING_NONE);
            schema.string("server_name", serverName);
        });
    }

    /**
     * JALON DE VENTE : {@link #PENDING_RESERVED} vers {@link #PENDING_COMMITTED}, en compare-and-set.
     * <p>
     * A appeler IMMEDIATEMENT apres que les items ont quitte l'inventaire du vendeur, sur le meme
     * thread et avant tout autre travail. C'est cette seule ecriture qui distingue les deux
     * abandons possibles d'une vente :
     * <ul>
     *   <li>tant qu'elle n'a pas eu lieu, une reservation abandonnee doit etre SUPPRIMEE - le
     *   vendeur a garde son lot, le lui rendre le dupliquerait ;</li>
     *   <li>une fois qu'elle a eu lieu, une reservation abandonnee doit rester RECLAMABLE - le
     *   vendeur a perdu son lot, la supprimer le detruirait.</li>
     * </ul>
     * L'appelant DOIT interrompre la vente si le compte rendu n'est pas 1 : la ligne n'est alors
     * plus sa reservation, et la publier reviendrait a publier l'annonce d'un autre etat.
     *
     * @param itemId identifier of the reserved listing
     * @return 1 si le jalon est pose, 0 si la ligne n'est plus une reservation non engagee
     */
    public int markReservationCommitted(int itemId) {
        try {
            return SchemaBuilder.update(getTableName(), schema -> {
                schema.where("id", itemId);
                schema.where("storage_type", StorageType.DELETED.name());
                schema.where("pending_publish", PENDING_RESERVED);
                schema.object("pending_publish", PENDING_COMMITTED);
            }).execute(this.connection, getLogger());
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to commit reservation " + itemId, exception);
        }
    }

    /**
     * RECUL DU JALON : {@link #PENDING_COMMITTED} vers {@link #PENDING_RESERVED}, en compare-and-set.
     * <p>
     * A appeler AVANT de rendre physiquement le lot au vendeur sur un chemin de compensation, et
     * uniquement si le compte rendu est 1. L'ordre est volontaire : si le processus meurt entre ce
     * recul et la remise des items, la ligne vaut {@link #PENDING_RESERVED} et sera SUPPRIMEE au
     * prochain demarrage - le lot est perdu, mais il n'est pas duplique. L'ordre inverse (rendre
     * puis reculer) laisserait au contraire une ligne reclamable EN PLUS du lot deja rendu.
     *
     * @param itemId identifier of the committed reservation
     * @return 1 si le jalon a ete recule, 0 si la ligne n'est plus une reservation engagee
     */
    public int markReservationUncommitted(int itemId) {
        try {
            return SchemaBuilder.update(getTableName(), schema -> {
                schema.where("id", itemId);
                schema.where("storage_type", StorageType.DELETED.name());
                schema.where("pending_publish", PENDING_COMMITTED);
                schema.object("pending_publish", PENDING_RESERVED);
            }).execute(this.connection, getLogger());
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to roll back reservation " + itemId, exception);
        }
    }

    /**
     * Publie une annonce ENGAGEE : DELETED vers LISTED, en compare-and-set.
     * <p>
     * La garde porte sur {@link #PENDING_COMMITTED} et non sur {@link #PENDING_RESERVED} : on ne
     * publie que ce dont le vendeur s'est deja dessaisi. Une reservation dont le jalon n'a pas ete
     * pose - les items sont encore dans l'inventaire - ne peut donc PAS devenir une annonce
     * achetable.
     * <p>
     * L'annonce n'apparait dans aucun hotel des ventes, sur aucun serveur, tant que cet UPDATE
     * n'a pas eu lieu : une panne au milieu de la creation ne laisse plus une annonce VIDE et
     * ACHETABLE en base partagee, mais une simple reservation invisible.
     *
     * @param itemId identifier of the committed reservation
     * @return 1 si publiee, 0 si la reservation a disparu, n'est pas engagee, ou est deja publiee
     */
    public int publishListing(int itemId) {
        try {
            return SchemaBuilder.update(getTableName(), schema -> {
                schema.where("id", itemId);
                schema.where("storage_type", StorageType.DELETED.name());
                schema.where("pending_publish", PENDING_COMMITTED);
                schema.object("storage_type", StorageType.LISTED.name());
                schema.object("pending_publish", PENDING_NONE);
            }).execute(this.connection, getLogger());
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to publish listing " + itemId, exception);
        }
    }

    /**
     * Rend RECLAMABLE une reservation ENGAGEE : DELETED vers EXPIRED, en compare-and-set.
     * <p>
     * Chemin "le lot n'a pas pu etre rendu en main propre" (vendeur deconnecte, tache de region
     * jamais executee sous Folia). La garde {@link #PENDING_COMMITTED} est ce qui rend l'operation
     * SURE : une reservation non engagee - dont le vendeur a garde le lot - ne peut pas passer par
     * ici.
     *
     * @param itemId identifier of the committed reservation
     * @return 1 si rendue reclamable, 0 si la ligne n'est plus une reservation engagee
     */
    public int makeReservationClaimable(int itemId) {
        try {
            return SchemaBuilder.update(getTableName(), schema -> {
                schema.where("id", itemId);
                schema.where("storage_type", StorageType.DELETED.name());
                schema.where("pending_publish", PENDING_COMMITTED);
                schema.object("storage_type", StorageType.EXPIRED.name());
                // 0 = n'expire jamais (ZItem.isExpired teste expiredAt.getTime() != 0) :
                // le vendeur peut reclamer son lot sans limite de temps.
                schema.object("expired_at", new Date(0));
                schema.object("pending_publish", PENDING_NONE);
            }).execute(this.connection, getLogger());
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to make reservation " + itemId + " claimable", exception);
        }
    }

    /**
     * Supprime une reservation NON ENGAGEE qui n'a pas pu etre publiee.
     * <p>
     * La garde {@link #PENDING_RESERVED} est le coeur de la surete : seule une reservation dont le
     * vendeur a encore le lot peut etre detruite. Une reservation engagee
     * ({@link #PENDING_COMMITTED}) ne matche pas et survit, pour rester reclamable.
     * <p>
     * Les contenus sont supprimes EXPLICITEMENT et APRES la ligne parente. La cle etrangere
     * auction_items.item_id vers items.id est bien declaree ON DELETE CASCADE, mais SQLite - le
     * backend par defaut - n'applique les cles etrangeres que si {@code PRAGMA foreign_keys = ON}
     * a ete emis, ce que Sarah ne fait nulle part : la cascade ne s'y declenche donc PAS. L'ordre
     * (parent d'abord) est volontaire : si le DELETE parent ne matche rien, on n'a pas vide le
     * contenu d'une ligne qui allait rester reclamable.
     *
     * @param itemId identifier of the reservation to drop
     * @return the number of deleted rows
     */
    public int deleteReservation(int itemId) {
        int deleted = delete(schema -> {
            schema.where("id", itemId);
            schema.where("storage_type", StorageType.DELETED.name());
            schema.where("pending_publish", PENDING_RESERVED);
        });
        if (deleted == 1) deleteReservationContents(itemId);
        return deleted;
    }

    /**
     * Supprime les contenus d'une reservation dont la ligne parente vient de disparaitre.
     * <p>
     * N'echoue jamais l'appelant : la ligne parente est deja partie, un contenu residuel n'est
     * plus atteignable (le chargement passe toujours par les identifiants d'items non DELETED),
     * il n'est qu'un dechet a journaliser.
     *
     * @param itemId identifier of the reservation whose contents must go
     */
    private void deleteReservationContents(int itemId) {
        try {
            Schema schema = SchemaBuilder.delete(Tables.AUCTION_ITEMS);
            schema.where("item_id", itemId);
            schema.execute(this.connection, getLogger());
        } catch (Throwable throwable) {
            this.plugin.getLogger().warning("[ZAH] Unable to delete the contents of reservation " + itemId
                    + " (its parent row is already gone, those rows are unreachable leftovers): " + throwable);
        }
    }

    /**
     * Balaye les reservations de vente qu'un crash a laissees non publiees, en traitant SEPAREMENT
     * les deux abandons possibles.
     * <p>
     * C'est ici que se joue la duplication : une reservation orpheline n'apprend RIEN sur le sort
     * du lot si l'on ne regarde pas {@code pending_publish}.
     * <ul>
     *   <li>{@link #PENDING_COMMITTED} : le vendeur s'etait deja dessaisi de son lot, la ligne
     *   passe en EXPIRED, sans expiration, et redevient reclamable par lui.</li>
     *   <li>{@link #PENDING_RESERVED} : le vendeur avait ENCORE son lot dans son inventaire. La
     *   ligne et ses contenus sont SUPPRIMES. La rendre reclamable donnerait au vendeur un second
     *   exemplaire de son lot : c'est exactement la duplication que ce jalon existe pour fermer.</li>
     * </ul>
     * Les lignes DELETED anterieures a cette version portent NULL et ne sont jamais touchees, ni
     * dans un sens ni dans l'autre.
     *
     * @param createdBefore ne traiter que les reservations plus vieilles que cette date, pour ne
     *                      jamais toucher a la vente en cours d'un autre noeud du cluster
     * @return le bilan du balayage
     */
    public OrphanReservationSweep recoverOrphanReservations(long createdBefore) {
        int recovered = recoverCommittedReservations(createdBefore);
        List<Integer> purged = purgeUncommittedReservations(createdBefore);
        return new OrphanReservationSweep(recovered, purged);
    }

    /**
     * Rend reclamables les reservations ENGAGEES orphelines : le lot a quitte l'inventaire du
     * vendeur, seule cette bascule evite de le detruire.
     *
     * @param createdBefore borne haute de creation
     * @return le nombre de reservations rendues reclamables
     */
    private int recoverCommittedReservations(long createdBefore) {
        try {
            return SchemaBuilder.update(getTableName(), schema -> {
                schema.where("pending_publish", PENDING_COMMITTED);
                schema.where("storage_type", StorageType.DELETED.name());
                schema.where("created_at", "<", new Date(createdBefore));
                schema.object("storage_type", StorageType.EXPIRED.name());
                // 0 = n'expire jamais (ZItem.isExpired teste expiredAt.getTime() != 0) :
                // le vendeur peut reclamer son lot sans limite de temps.
                schema.object("expired_at", new Date(0));
                schema.object("pending_publish", PENDING_NONE);
            }).execute(this.connection, getLogger());
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to recover committed listing reservations", exception);
        }
    }

    /**
     * Supprime les reservations NON ENGAGEES orphelines : le vendeur a garde son lot, il n'y a
     * rien a lui rendre.
     * <p>
     * Volontairement ligne par ligne et non en lot : chaque suppression garde alors sa propre
     * garde de compare-and-set, et une ligne qu'un autre noeud vient de faire evoluer ne peut pas
     * voir ses contenus effaces au passage.
     *
     * @param createdBefore borne haute de creation
     * @return les identifiants reellement supprimes
     */
    private List<Integer> purgeUncommittedReservations(long createdBefore) {
        List<ItemDTO> orphans = selectOrFail(ItemDTO.class, schema -> {
            schema.where("pending_publish", PENDING_RESERVED);
            schema.where("storage_type", StorageType.DELETED.name());
            schema.where("created_at", "<", new Date(createdBefore));
        });

        List<Integer> purged = new ArrayList<>(orphans.size());
        for (ItemDTO orphan : orphans) {
            if (deleteReservation(orphan.id()) == 1) purged.add(orphan.id());
        }
        return purged;
    }

    /**
     * Charge toutes les annonces vivantes (tout sauf DELETED).
     * <p>
     * {@code selectOrFail} et non {@code select} : c'est le chargement de masse du demarrage.
     * Un echec SQL avale par {@code Repository.select} rendait une liste VIDE, indiscernable
     * d'un hotel des ventes reellement vide, et le plugin demarrait en ayant perdu tout le
     * catalogue (C-001).
     *
     * @return every non-deleted listing row
     * @throws IllegalStateException if the query failed
     */
    public List<ItemDTO> select() {
        return selectOrFail(ItemDTO.class, schema -> schema.where("storage_type", "!=", StorageType.DELETED.name()));
    }

    /**
     * Repositionne dans un conteneur RECLAMABLE une ligne deja passee a DELETED dont la remise
     * physique n'a finalement pas pu avoir lieu (joueur deconnecte, entite retiree sur Folia).
     * <p>
     * Volontairement SANS la garde {@code storage_type = LISTED} de {@code createUpdateSchema} :
     * la source est ici DELETED, pas LISTED. La garde de compare-and-set porte sur DELETED, ce
     * qui rend l'operation idempotente et sans effet si un autre noeud a deja bouge la ligne.
     * Volontairement SANS la garde {@code whereNull(buyer_unique_id)} : une annonce achetee puis
     * non remise doit pouvoir redevenir reclamable par son acheteur.
     *
     * @param item        the item whose row must be restored
     * @param destination the claimable bucket to restore the row into
     * @return le nombre de lignes reellement modifiees (1 = restauree, 0 = deja bougee ailleurs)
     */
    public int restoreFromDeleted(Item item, StorageType destination) {
        try {
            return SchemaBuilder.update(getTableName(), schema -> {
                schema.where("id", item.getId());
                schema.where("storage_type", StorageType.DELETED.name());
                schema.object("storage_type", destination.name());
                schema.object("expired_at", item.getExpiredAt());
            }).execute(this.connection, getLogger());
        } catch (SQLException exception) {
            // Sarah leve en realite une DatabaseException (RuntimeException) : ce catch est un
            // filet de compilation, il ne doit jamais avaler l'echec silencieusement.
            throw new IllegalStateException("Unable to restore item " + item.getId() + " from DELETED", exception);
        }
    }

    /**
     * @param item        item to update
     * @param storageType destination storage state
     * @deprecated aucune garde de transition n'est posee (hors la garde historique vers EXPIRED) :
     * l'UPDATE ecrase la ligne quel que soit son etat courant, et le nombre de lignes modifiees
     * est jete. Conserve uniquement pour les chemins non encore audites.
     * Utiliser {@link #updateItem(Item, StorageType, StorageType)}.
     */
    @Deprecated
    public void updateItem(Item item, StorageType storageType) {
        this.update(createUpdateSchema(item, storageType));
    }

    /**
     * Compare-and-set : l'UPDATE n'aboutit QUE si la ligne porte encore l'etat source attendu.
     * C'est la seule barriere que la base oppose aux transitions concurrentes entre serveurs.
     *
     * @param item item to move
     * @param from storage state the row is expected to still carry
     * @param to   destination storage state
     * @return the number of affected rows; {@code 0} means the race was lost
     */
    public int updateItem(Item item, StorageType from, StorageType to) {
        return updateReturning(createCasUpdateSchema(item, from, to));
    }

    public Optional<ItemDTO> select(int id) {
        return select(ItemDTO.class, schema -> {
            schema.where("id", id);
            schema.where("storage_type", "!=", StorageType.DELETED.name());
        }).stream().findFirst();
    }

    /**
     * Batch historique, SANS garde de transition ni remontee de rowcount.
     * Conserve tel quel tant que le dernier appelant n'est pas migre vers
     * {@link #updateItems(Map, StorageType)}.
     *
     * @param itemsByStorageType destination storage state to the items to move
     */
    public void updateItems(Map<StorageType, List<Item>> itemsByStorageType) {
        for (Map.Entry<StorageType, List<Item>> entry : itemsByStorageType.entrySet()) {
            StorageType storageType = entry.getKey();
            List<Item> items = entry.getValue();
            if (items.isEmpty()) continue;

            // Update each item individually using the consumer pattern
            for (Item item : items) {
                update(createUpdateSchema(item, storageType));
            }
        }
    }

    /**
     * Batch compare-and-set. Rend les identifiants des items qui ont PERDU la course, afin que
     * l'appelant purge sa memoire au lieu de garder un fantome reclamable.
     *
     * @param itemsByStorageType destination -&gt; items to move
     * @param from               storage state all these rows are expected to still carry
     * @return the ids whose row did not move
     */
    public List<Integer> updateItems(Map<StorageType, List<Item>> itemsByStorageType, StorageType from) {

        List<Integer> staleIds = new ArrayList<>();

        for (Map.Entry<StorageType, List<Item>> entry : itemsByStorageType.entrySet()) {
            StorageType destination = entry.getKey();
            List<Item> items = entry.getValue();
            if (items.isEmpty()) continue;

            // UpdateBatchRequest construit le SQL a partir du PREMIER schema seulement : tous les
            // schemas d'un meme lot doivent donc avoir exactement les memes colonnes. La seule
            // variable, a destination fixee, est la presence de buyer_unique_id.
            Map<Boolean, List<Item>> byShape = items.stream().collect(Collectors.partitioningBy(item ->
                    (destination == StorageType.PURCHASED || destination == StorageType.DELETED) && item.getBuyerUniqueId() != null));

            for (List<Item> group : byShape.values()) {
                if (group.isEmpty()) continue;
                for (int offset = 0; offset < group.size(); offset += BATCH_SIZE) {
                    List<Item> chunk = group.subList(offset, Math.min(offset + BATCH_SIZE, group.size()));
                    var schemas = chunk.stream().map(item -> createUpdateSchema(createCasUpdateSchema(item, from, destination))).toList();
                    int affected = updateReturning(schemas);
                    if (affected != chunk.size()) {
                        // Soit une course perdue, soit un pilote qui rend SUCCESS_NO_INFO (-2).
                        // On tranche par une relecture ciblee : c'est le seul verdict fiable.
                        staleIds.addAll(findStale(chunk, destination));
                    }
                }
            }
        }

        return staleIds;
    }

    /**
     * Relit l'etat reel des lignes d'un lot et rend celles qui ne sont pas arrivees a destination.
     */
    private List<Integer> findStale(List<Item> chunk, StorageType destination) {
        var ids = chunk.stream().map(item -> String.valueOf(item.getId())).toList();

        // Volontairement une boucle et non Collectors.toMap : toMap leve une NullPointerException
        // si une ligne remonte un storage_type illisible, ce qui transformerait une simple
        // verification de course en echec du lot entier.
        Map<Integer, StorageType> actual = new HashMap<>();
        for (ItemDTO dto : select(ids)) {
            actual.putIfAbsent(dto.id(), dto.storage_type());
        }

        List<Integer> staleIds = new ArrayList<>();
        for (Item item : chunk) {
            if (actual.get(item.getId()) != destination) staleIds.add(item.getId());
        }
        return staleIds;
    }

    private Consumer<Schema> createUpdateSchema(Item item, StorageType storageType) {
        return schema -> {
            schema.where("id", item.getId());
            // Guard: an item may only transition INTO EXPIRED while it is still LISTED.
            // The expiration path (ExpireService) is not cluster-aware, so without this a
            // local expiration on one server could overwrite a row that another server
            // already set to DELETED/PURCHASED (a sold item) and resurrect it for the
            // seller -> duplication. With the guard the UPDATE matches 0 rows in that case.
            if (storageType == StorageType.EXPIRED) {
                schema.where("storage_type", StorageType.LISTED.name());
            }
            schema.string("storage_type", storageType.name());
            if (storageType != StorageType.DELETED) {
                schema.object("expired_at", item.getExpiredAt());
            }
            if (storageType == StorageType.PURCHASED || storageType == StorageType.DELETED) {
                if (item.getBuyerUniqueId() != null) {
                    schema.uuid("buyer_unique_id", item.getBuyerUniqueId());
                }
            }
        };
    }

    /**
     * Schema d'UPDATE avec compare-and-set. Contrairement au schema historique, la clause
     * {@code where storage_type = from} est posee sur TOUTES les destinations, et une transition
     * vers PURCHASED exige en plus que la ligne n'ait pas deja un acheteur.
     */
    private Consumer<Schema> createCasUpdateSchema(Item item, StorageType expectedFrom, StorageType storageType) {
        return schema -> {
            schema.where("id", item.getId());
            schema.where("storage_type", expectedFrom.name());
            if (storageType == StorageType.PURCHASED) {
                schema.whereNull("buyer_unique_id");
            }
            schema.string("storage_type", storageType.name());
            if (storageType != StorageType.DELETED) {
                schema.object("expired_at", item.getExpiredAt());
            }
            if (storageType == StorageType.PURCHASED || storageType == StorageType.DELETED) {
                if (item.getBuyerUniqueId() != null) {
                    schema.uuid("buyer_unique_id", item.getBuyerUniqueId());
                }
            }
        };
    }

    /**
     * Relit un lot d'annonces par identifiant.
     * <p>
     * Clause IN PAGINEE et echec PROPAGE : au-dela du plafond de parametres du pilote, la
     * requete etait rejetee et {@code Repository.select} rendait une liste vide, ce qui faisait
     * passer tout le lot pour "disparu" (C-001).
     *
     * @param ids the listing identifiers
     * @return the matching rows
     * @throws IllegalStateException if the query failed
     */
    public List<ItemDTO> select(List<String> ids) {
        return selectInOrFail(ItemDTO.class, "id", ids);
    }
}
