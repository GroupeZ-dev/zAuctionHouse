package fr.maxlego08.zauctionhouse.storage.repository.repositeries;

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

    public List<PlayerDTO> select() {
        return selectAll(PlayerDTO.class);
    }

    public List<PlayerDTO> select(List<String> uuids) {
        return select(PlayerDTO.class, schema -> schema.whereIn("unique_id", uuids));
    }

    public String select(UUID uniqueId) {
        return select(PlayerDTO.class, schema -> schema.where("unique_id", uniqueId.toString())).stream().findFirst().map(PlayerDTO::name).orElse(null);
    }

    public UUID selectByName(String name) {
        return select(PlayerDTO.class, schema -> schema.where("name", name)).stream().findFirst().map(PlayerDTO::unique_id).orElse(null);
    }
}
