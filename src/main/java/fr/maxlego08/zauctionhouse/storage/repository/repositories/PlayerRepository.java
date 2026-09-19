package fr.maxlego08.zauctionhouse.storage.repository.repositories;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.storage.Repository;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.PlayerDTO;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class PlayerRepository extends Repository {

    public PlayerRepository(AuctionPlugin plugin, DatabaseConnection connection) {
        super(plugin, connection, Tables.PLAYERS);
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
