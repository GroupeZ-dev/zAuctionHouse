package fr.maxlego08.zauctionhouse.storage.repository.repositories;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.storage.Repository;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.PlayerDTO;
import fr.maxlego08.zauctionhouse.api.storage.dto.AdminPlayerDTO;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class PlayerRepository extends Repository {

    public PlayerRepository(AuctionPlugin plugin, DatabaseConnection connection) {
        super(plugin, connection, Tables.PLAYERS);
    }

    /** One row per player with auction activity; no item contents are loaded for this directory. */
    public List<AdminPlayerDTO> selectAdminPlayers() {
        String sql = """
                SELECT p.unique_id, p.name, SUM(h.listed) AS listed, SUM(h.sold) AS sold,
                       SUM(h.purchased) AS purchased
                FROM %prefix%players p
                JOIN (
                    SELECT seller_unique_id AS unique_id,
                           SUM(CASE WHEN storage_type = 'LISTED' AND (expired_at > ? OR expired_at = ?) THEN 1 ELSE 0 END) AS listed,
                           0 AS sold, 0 AS purchased
                    FROM %prefix%items WHERE pending_publish IS NULL OR pending_publish = 0
                    GROUP BY seller_unique_id
                    UNION ALL
                    SELECT buyer_unique_id, 0, 0, 0 FROM %prefix%items
                    WHERE buyer_unique_id IS NOT NULL GROUP BY buyer_unique_id
                    UNION ALL
                    SELECT player_unique_id, 0, 0,
                           SUM(CASE WHEN log_type = 'PURCHASE' THEN 1 ELSE 0 END)
                    FROM %prefix%logs GROUP BY player_unique_id
                    UNION ALL
                    SELECT target_unique_id, 0,
                           SUM(CASE WHEN log_type = 'PURCHASE' THEN 1 ELSE 0 END), 0
                    FROM %prefix%logs WHERE target_unique_id IS NOT NULL GROUP BY target_unique_id
                    UNION ALL
                    SELECT player_unique_id, 0, 0, 0 FROM %prefix%transactions GROUP BY player_unique_id
                ) h ON h.unique_id = p.unique_id
                GROUP BY p.unique_id, p.name
                """;
        sql = this.connection.getDatabaseConfiguration().replacePrefix(sql);
        var result = new java.util.ArrayList<AdminPlayerDTO>();
        try (var jdbc = this.connection.getConnection(); var statement = jdbc.prepareStatement(sql)) {
            statement.setTimestamp(1, new java.sql.Timestamp(System.currentTimeMillis()));
            statement.setTimestamp(2, new java.sql.Timestamp(0));
            try (var rows = statement.executeQuery()) {
                while (rows.next()) result.add(new AdminPlayerDTO(
                        UUID.fromString(rows.getString("unique_id")), rows.getString("name"),
                        rows.getLong("listed"), rows.getLong("sold"), rows.getLong("purchased")));
            }
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Cannot load admin player statistics", exception);
        }
        return result;
    }

    public void upsertPlayer(Player player) {
        this.upsert(schema -> {
            schema.uuid("unique_id", player.getUniqueId()).primary();
            schema.string("name", player.getName());
        });
    }

    public void upsertPlayer(UUID uniqueId, String name) {
        this.upsert(schema -> {
            schema.uuid("unique_id", uniqueId).primary();
            schema.string("name", name);
        });
    }

    /**
     * Charge la totalite de l'annuaire des joueurs.
     * <p>
     * {@code selectAllOrFail} et non {@code selectAll} : un echec avale rendait une liste VIDE,
     * et le chargement mettait alors en QUARANTAINE la totalite des annonces (vendeur
     * introuvable) au lieu de tuer le demarrage bruyamment (C-001).
     *
     * @return every known player row
     * @throws IllegalStateException if the query failed
     */
    public List<PlayerDTO> select() {
        return selectAllOrFail(PlayerDTO.class);
    }

    /**
     * Charge un lot de joueurs par UUID.
     * <p>
     * Clause IN PAGINEE et echec PROPAGE : sur un gros reseau, la liste d'UUID depassait le
     * plafond de parametres du pilote et la requete etait rejetee, silencieusement (C-001).
     *
     * @param uuids the player unique ids, as strings
     * @return the matching rows
     * @throws IllegalStateException if the query failed
     */
    public List<PlayerDTO> select(List<String> uuids) {
        return selectInOrFail(PlayerDTO.class, "unique_id", uuids);
    }

    public String select(UUID uniqueId) {
        return select(PlayerDTO.class, schema -> schema.where("unique_id", uniqueId.toString())).stream().findFirst().map(PlayerDTO::name).orElse(null);
    }

    public UUID selectByName(String name) {
        return select(PlayerDTO.class, schema -> schema.where("name", name)).stream().findFirst().map(PlayerDTO::unique_id).orElse(null);
    }
}
