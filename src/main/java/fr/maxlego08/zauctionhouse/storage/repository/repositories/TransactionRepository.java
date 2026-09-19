package fr.maxlego08.zauctionhouse.storage.repository.repositories;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.storage.Repository;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.TransactionDTO;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TransactionRepository extends Repository {

    public TransactionRepository(AuctionPlugin plugin, DatabaseConnection connection) {
        super(plugin, connection, Tables.TRANSACTIONS);
    }

    public void create(Item item, UUID playerUniqueId, String economyName, BigDecimal moneyBefore, BigDecimal moneyAfter, BigDecimal value, TransactionStatus status) {
        insert(schema -> {
            schema.object("item_id", item.getId());
            schema.uuid("player_unique_id", playerUniqueId);
            schema.string("economy_name", economyName);
            schema.decimal("before", moneyBefore);
            schema.decimal("after", moneyAfter);
            schema.decimal("value", value);
            schema.string("status", status.name());
        });
    }

    public List<TransactionDTO> selectByPlayer(UUID playerUniqueId) {
        return select(TransactionDTO.class, schema -> schema.where("player_unique_id", playerUniqueId.toString()));
    }

    public List<TransactionDTO> selectByPlayerAndStatus(UUID playerUniqueId, TransactionStatus status) {
        return select(TransactionDTO.class, schema -> schema.where("player_unique_id", playerUniqueId.toString()).where("status", status.name()));
    }

    public void updateStatus(int transactionId, TransactionStatus status) {
        updateStatus(List.of(transactionId), status);
    }

    /**
     * @param transactionIds the transactions to close
     * @param status         the target status
     * @return the number of rows actually updated (a shortfall means a concurrent claim won)
     */
    public int updateStatus(Collection<Integer> transactionIds, TransactionStatus status) {
        if (transactionIds == null || transactionIds.isEmpty()) return 0;

        var schemas = transactionIds.stream()
                .map(transactionId -> createUpdateSchema(schema -> {
                    schema.where("id", transactionId);
                    // Compare-and-set : le perdant d'une course matche 0 ligne au lieu
                    // de re-marquer une ligne deja cloturee par quelqu'un d'autre.
                    schema.where("status", TransactionStatus.PENDING.name());
                    schema.string("status", status.name());
                }))
                .toList();
        return updateReturning(schemas);
    }

    /**
     * Reserve, en UNE seule ecriture atomique, toutes les lignes PENDING encore libres du joueur.
     * <p>
     * Le nombre de lignes rendu par la base est le seul arbitre : deux claims concurrents
     * (bouton, /ah claim, auto-claim, ou deux serveurs du cluster) ne peuvent pas remporter les
     * memes lignes, le second en obtient zero.
     * <p>
     * {@code claim_reserved_at} est ecrit a titre PUREMENT INFORMATIF (diagnostic support). Il
     * porte l'horloge du serveur qui reserve et ne doit JAMAIS arbitrer une liberation :
     * comparer cette date a l'horloge d'un AUTRE serveur (derive NTP, ou claim plus long que le
     * seuil) revient a liberer une reservation vivante, donc a payer deux fois. La recuperation
     * des reservations abandonnees passe exclusivement par le prefixe du jeton
     * ({@link #selectReservedTokens(UUID, String)}), c'est-a-dire par l'identite du serveur.
     *
     * @param playerUniqueId the player whose pending money is being claimed
     * @param claimToken     the token identifying this claim attempt
     * @return the number of rows this claim actually reserved
     */
    public int reservePending(UUID playerUniqueId, String claimToken) {
        return updateReturning(schema -> {
            schema.where("player_unique_id", playerUniqueId.toString());
            schema.where("status", TransactionStatus.PENDING.name());
            schema.whereNull("claim_token");
            schema.string("claim_token", claimToken);
            schema.object("claim_reserved_at", new Date());
        });
    }

    /**
     * Reads back the rows reserved by a given claim attempt.
     *
     * @param claimToken the token used by {@link #reservePending(UUID, String)}
     * @return the reserved transactions
     */
    public List<TransactionDTO> selectByClaimToken(String claimToken) {
        return select(TransactionDTO.class, schema -> schema.where("claim_token", claimToken));
    }

    /**
     * Cloture les lignes REELLEMENT payees. Le compare-and-set porte a la fois sur le jeton
     * (ce sont bien mes lignes) et sur le statut (elles n'ont pas ete cloturees entre-temps).
     *
     * @param transactionIds the transactions that were actually credited
     * @param claimToken     the token that reserved them
     * @return the number of rows actually closed
     */
    public int finishClaim(Collection<Integer> transactionIds, String claimToken) {
        if (transactionIds == null || transactionIds.isEmpty()) return 0;
        var schemas = transactionIds.stream().map(transactionId -> createUpdateSchema(schema -> {
            schema.where("id", transactionId);
            schema.where("claim_token", claimToken);
            schema.where("status", TransactionStatus.PENDING.name());
            schema.string("status", TransactionStatus.RETRIEVED.name());
        })).toList();
        return updateReturning(schemas);
    }

    /**
     * Rend reclamables les lignes reservees mais NON payees (economie introuvable, depot en
     * echec, joueur deconnecte). Sans cet appel, l'argent resterait bloque jusqu'a la
     * recuperation par le serveur proprietaire du jeton.
     *
     * @param transactionIds the reserved transactions that were not paid
     * @param claimToken     the token that reserved them
     * @return the number of rows released
     */
    public int releaseClaim(Collection<Integer> transactionIds, String claimToken) {
        if (transactionIds == null || transactionIds.isEmpty()) return 0;
        var schemas = transactionIds.stream().map(transactionId -> createUpdateSchema(schema -> {
            schema.where("id", transactionId);
            schema.where("claim_token", claimToken);
            schema.object("claim_token", null);
            schema.object("claim_reserved_at", null);
        })).toList();
        return updateReturning(schemas);
    }

    /**
     * Libere, en UNE ecriture, toutes les lignes ENCORE PENDING portant ce jeton.
     * <p>
     * Le jeton est l'unique critere : aucune horloge n'intervient, donc aucune comparaison entre
     * l'horloge du serveur qui a reserve et celle du serveur qui lit. L'appelant est responsable
     * de ne fournir qu'un jeton dont il a la PROPRIETE (voir
     * {@link #selectReservedTokens(UUID, String)}) et qui n'appartient pas a un claim vivant.
     * Le garde {@code status = PENDING} evite de deshabiller une ligne deja cloturee.
     *
     * @param claimToken the token whose reservation must be dropped
     * @return the number of rows released
     */
    public int releaseReservation(String claimToken) {
        if (claimToken == null || claimToken.isEmpty()) return 0;
        return updateReturning(schema -> {
            schema.where("claim_token", claimToken);
            schema.where("status", TransactionStatus.PENDING.name());
            schema.object("claim_token", null);
            schema.object("claim_reserved_at", null);
        });
    }

    /**
     * Liste les jetons de reservation encore poses sur les lignes PENDING d'un joueur et dont le
     * prefixe designe un serveur donne.
     * <p>
     * C'est le remplacant du filet TTL : au lieu de demander "cette reservation est-elle vieille
     * selon MON horloge ?" (question a laquelle deux serveurs repondent differemment) on
     * demande "cette reservation est-elle la MIENNE ?", dont la reponse ne depend d'aucune
     * horloge. Un serveur ne peut donc plus liberer la reservation vivante d'un autre.
     * <p>
     * En cas d'echec de lecture, {@code select} rend une liste vide : on ne libere rien. C'est le
     * sens sur de l'echec (argent momentanement bloque plutot que paye deux fois).
     *
     * @param playerUniqueId the player whose reservations are inspected
     * @param tokenPrefix    the token prefix identifying the owning server
     * @return the distinct tokens still holding PENDING rows of this player for that server
     */
    public List<String> selectReservedTokens(UUID playerUniqueId, String tokenPrefix) {
        if (tokenPrefix == null || tokenPrefix.isEmpty()) return List.of();

        var rows = select(ClaimReservationRow.class, schema -> {
            schema.where("player_unique_id", playerUniqueId.toString());
            schema.where("status", TransactionStatus.PENDING.name());
            schema.whereNotNull("claim_token");
            // LIKE parametre : la valeur part en '?', elle n'est jamais concatenee dans le SQL.
            // Le prefixe ne contient que des caracteres hexadecimaux et un tiret, donc aucun
            // joker. Le filtre exact est refait cote Java ci-dessous, car LIKE est insensible a
            // la casse avec les collations MySQL par defaut.
            schema.where("claim_token", "LIKE", tokenPrefix + "%");
        });

        Set<String> tokens = new LinkedHashSet<>();
        for (ClaimReservationRow row : rows) {
            String token = row.claim_token();
            if (token != null && token.startsWith(tokenPrefix)) tokens.add(token);
        }
        return new ArrayList<>(tokens);
    }

    /**
     * Projection minimale d'une ligne reservee.
     * <p>
     * {@code TransactionDTO} n'expose pas {@code claim_token} (record du module API, hors
     * perimetre de ce correctif) : Sarah mappe les colonnes sur les composants du record par NOM,
     * un record dedie suffit donc a relire le jeton sans toucher a l'API publique.
     *
     * @param id          the transaction identifier
     * @param claim_token the token currently holding the row
     */
    public record ClaimReservationRow(int id, String claim_token) {
    }
}
