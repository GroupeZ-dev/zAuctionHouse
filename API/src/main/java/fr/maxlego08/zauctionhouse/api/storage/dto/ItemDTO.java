package fr.maxlego08.zauctionhouse.api.storage.dto;

import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

public record ItemDTO(int id, ItemType item_type, UUID seller_unique_id, UUID buyer_unique_id, BigDecimal price,
                      String economy_name, StorageType storage_type, String server_name, Date created_at, Date updated_at,
                      Date expired_at) {
}
