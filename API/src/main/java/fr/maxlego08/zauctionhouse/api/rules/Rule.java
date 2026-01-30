package fr.maxlego08.zauctionhouse.api.rules;

import org.bukkit.inventory.ItemStack;

public interface Rule {
    /**
     * Checks whether the given item stack matches this rule.
     *
     * @param itemStack the item stack to test
     * @return true if the item stack matches this rule, false otherwise
     */
    boolean matches(ItemStack itemStack);
}
