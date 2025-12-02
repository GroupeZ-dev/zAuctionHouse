package fr.maxlego08.zauctionhouse.api.storage.dto;

import java.util.Date;

public record AuctionItemDTO(int id, int item_id, String itemstack, Date created_at, Date updated_at) {
}
