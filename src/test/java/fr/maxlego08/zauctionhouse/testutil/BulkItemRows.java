package fr.maxlego08.zauctionhouse.testutil;

import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Insertion EN MASSE de lignes de fixtures, en une seule transaction JDBC.
 * <p>
 * Les repositories ouvrent une connexion par requete : inserer 1 000 annonces une par une
 * coute plusieurs secondes sur un fichier SQLite. Ce helper ne sert QU'A preparer l'etat
 * initial d'un test (jamais a verifier le code teste) et ecrit les colonnes exactement comme
 * {@code ItemRepository.create} le fait (expired_at en INTEGER millis, pending_publish = 0).
 * <p>
 * L'appelant fournit une connexion JDBC ouverte ({@code DatabaseTestBase.jdbc()}) et la ferme.
 */
public final class BulkItemRows {

    private BulkItemRows() {
    }

    /**
     * Insere {@code count} annonces du vendeur donne dans l'etat demande et rend leurs
     * identifiants, dans l'ordre d'insertion.
     */
    public static List<Integer> insertItems(Connection jdbc, String tablePrefix, UUID seller, int count, StorageType storageType, long expiredAt) throws SQLException {
        String table = Tables.ITEMS.replace("%prefix%", tablePrefix);
        String sql = "INSERT INTO " + table + " (item_type, seller_unique_id, economy_name, price, storage_type, server_name, expired_at, pending_publish) VALUES (?, ?, ?, ?, ?, ?, ?, 0)";

        boolean autoCommit = jdbc.getAutoCommit();
        jdbc.setAutoCommit(false);
        try {
            long before = maxId(jdbc, table);
            try (PreparedStatement statement = jdbc.prepareStatement(sql)) {
                for (int index = 0; index < count; index++) {
                    statement.setString(1, ItemType.AUCTION.name());
                    statement.setString(2, seller.toString());
                    statement.setString(3, "vault");
                    statement.setBigDecimal(4, BigDecimal.TEN);
                    statement.setString(5, storageType.name());
                    statement.setString(6, MockPlugins.SERVER_NAME);
                    statement.setLong(7, expiredAt);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            jdbc.commit();

            List<Integer> ids = new ArrayList<>(count);
            try (PreparedStatement statement = jdbc.prepareStatement("SELECT id FROM " + table + " WHERE id > ? ORDER BY id")) {
                statement.setLong(1, before);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) ids.add(resultSet.getInt(1));
                }
            }
            return ids;
        } catch (SQLException exception) {
            jdbc.rollback();
            throw exception;
        } finally {
            jdbc.setAutoCommit(autoCommit);
        }
    }

    /**
     * Insere {@code perItem} contenus (charges utiles {@code payload-<itemId>-<n>}) pour chacune
     * des annonces donnees.
     */
    public static void insertPayloads(Connection jdbc, String tablePrefix, List<Integer> itemIds, int perItem) throws SQLException {
        String table = Tables.AUCTION_ITEMS.replace("%prefix%", tablePrefix);
        String sql = "INSERT INTO " + table + " (item_id, itemstack) VALUES (?, ?)";

        boolean autoCommit = jdbc.getAutoCommit();
        jdbc.setAutoCommit(false);
        try (PreparedStatement statement = jdbc.prepareStatement(sql)) {
            for (int itemId : itemIds) {
                for (int index = 0; index < perItem; index++) {
                    statement.setInt(1, itemId);
                    statement.setString(2, payload(itemId, index));
                    statement.addBatch();
                }
            }
            statement.executeBatch();
            jdbc.commit();
        } catch (SQLException exception) {
            jdbc.rollback();
            throw exception;
        } finally {
            jdbc.setAutoCommit(autoCommit);
        }
    }

    /**
     * Charge utile deterministe ecrite par {@link #insertPayloads}.
     */
    public static String payload(int itemId, int index) {
        return "payload-" + itemId + "-" + index;
    }

    private static long maxId(Connection jdbc, String table) throws SQLException {
        try (Statement statement = jdbc.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT COALESCE(MAX(id), 0) FROM " + table)) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }
}
