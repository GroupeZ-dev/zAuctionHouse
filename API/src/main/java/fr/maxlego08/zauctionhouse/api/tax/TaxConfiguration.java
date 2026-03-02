package fr.maxlego08.zauctionhouse.api.tax;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.List;

/**
 * Configuration for the tax system of an economy.
 */
public interface TaxConfiguration {

    /**
     * Checks if taxes are enabled for this economy.
     *
     * @return true if taxes are enabled
     */
    boolean isEnabled();

    /**
     * Gets the type of tax applied.
     *
     * @return the tax type
     */
    TaxType getTaxType();

    /**
     * Gets how the tax amount is calculated.
     *
     * @return the tax amount type
     */
    TaxAmountType getAmountType();

    /**
     * Gets the base tax amount (percentage or fixed value).
     *
     * @return the tax amount
     */
    double getAmount();

    /**
     * Gets the permission required to bypass taxes entirely.
     *
     * @return the bypass permission, or null if none
     */
    String getBypassPermission();

    /**
     * Gets the list of tax reductions available.
     *
     * @return list of tax reductions
     */
    List<TaxReduction> getReductions();

    /**
     * Checks if item-specific tax rules are enabled.
     *
     * @return true if item rules are enabled
     */
    boolean hasItemRules();

    /**
     * Gets the list of item-specific tax rules.
     *
     * @return list of item tax rules
     */
    List<ItemTaxRule> getItemRules();

    /**
     * Calculates the tax for a sell operation.
     *
     * @param player    the player selling the item
     * @param price     the sale price
     * @param itemStack the item being sold (for item-specific rules)
     * @return the tax calculation result
     */
    TaxResult calculateSellTax(Player player, BigDecimal price, ItemStack itemStack);

    /**
     * Calculates the tax for a purchase operation.
     *
     * @param player    the player buying the item
     * @param price     the purchase price
     * @param itemStack the item being purchased (for item-specific rules)
     * @return the tax calculation result
     */
    TaxResult calculatePurchaseTax(Player player, BigDecimal price, ItemStack itemStack);

    /**
     * Checks if the player can bypass taxes.
     *
     * @param player the player to check
     * @return true if the player can bypass taxes
     */
    boolean canBypass(Player player);

    /**
     * Gets the tax reduction for a player, if any.
     *
     * @param player the player to check
     * @return the applicable TaxReduction, or null if none
     */
    TaxReduction getReduction(Player player);
}
