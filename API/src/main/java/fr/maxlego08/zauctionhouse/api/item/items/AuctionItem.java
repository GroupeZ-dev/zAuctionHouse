package fr.maxlego08.zauctionhouse.api.item.items;

import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public interface AuctionItem extends Item {

    List<ItemStack> getItemStacks();

    String getItemsAsString();
}
