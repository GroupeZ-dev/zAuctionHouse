package fr.maxlego08.zauctionhouse.api.history;

import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;

public record ItemLog(LogDTO log, Item item) {
}
