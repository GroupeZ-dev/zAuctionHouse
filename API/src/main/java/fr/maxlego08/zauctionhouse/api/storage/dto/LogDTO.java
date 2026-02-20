package fr.maxlego08.zauctionhouse.api.storage.dto;

import fr.maxlego08.zauctionhouse.api.log.LogType;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

public record LogDTO(int id, LogType log_type, int item_id, UUID player_unique_id, UUID target_unique_id,
                     String itemstack, BigDecimal price, String economy_name, String additional_data,
                     Date readed_at, Date created_at, Date updated_at) {

    /**
     * Checks if this log has been read by the target player.
     * @return true if the log has been read
     */
    public boolean isRead() {
        return readed_at != null;
    }
}
