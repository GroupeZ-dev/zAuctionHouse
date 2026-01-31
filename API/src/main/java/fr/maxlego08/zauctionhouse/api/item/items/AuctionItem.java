package fr.maxlego08.zauctionhouse.api.item.items;

import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public interface AuctionItem extends Item {

    List<ItemStack> getItemStacks();

    /**
     * Gets the main ItemStack for this auction item.
     * Used for category matching and display purposes.
     *
     * @return the first ItemStack in the list
     */
    default ItemStack getItemStack() {
        var itemStacks = getItemStacks();
        return itemStacks.isEmpty() ? null : itemStacks.getFirst();
    }

    String getItemsAsString();
}
