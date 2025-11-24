package fr.maxlego08.zauctionhouse.api.storage.dto;

import fr.maxlego08.zauctionhouse.api.item.StorageType;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

public record AuctionItemDTO(int id, UUID seller_unique_id, UUID buyer_unique_id, String itemstack, BigDecimal price,
                             String economy_name, StorageType storage_type, Date created_at, Date updated_at,
                             Date expired_at) {
}
