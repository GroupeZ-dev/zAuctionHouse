package fr.maxlego08.zauctionhouse.tax;

import fr.maxlego08.zauctionhouse.api.tax.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Implementation of {@link TaxConfiguration} for an economy's tax settings.
 */
public class ZTaxConfiguration implements TaxConfiguration {

    private final boolean enabled;
    private final TaxType taxType;
    private final TaxAmountType amountType;
    private final double amount;
    private final String bypassPermission;
    private final List<TaxReduction> reductions;
    private final boolean itemRulesEnabled;
    private final List<ItemTaxRule> itemRules;

    public ZTaxConfiguration(boolean enabled, TaxType taxType, TaxAmountType amountType, double amount,
                             String bypassPermission, List<TaxReduction> reductions,
                             boolean itemRulesEnabled, List<ItemTaxRule> itemRules) {
        this.enabled = enabled;
        this.taxType = taxType;
        this.amountType = amountType;
        this.amount = amount;
        this.bypassPermission = bypassPermission;
        this.reductions = reductions != null ? reductions : Collections.emptyList();
        this.itemRulesEnabled = itemRulesEnabled;
        // Sort item rules by priority (highest first)
        this.itemRules = itemRules != null
                ? itemRules.stream().sorted(Comparator.comparingInt(ItemTaxRule::getPriority).reversed()).toList()
                : Collections.emptyList();
    }

    /**
     * Creates a disabled tax configuration.
     */
    public static ZTaxConfiguration disabled() {
        return new ZTaxConfiguration(false, TaxType.SELL, TaxAmountType.PERCENTAGE, 0,
                null, Collections.emptyList(), false, Collections.emptyList());
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public TaxType getTaxType() {
        return taxType;
    }

    @Override
    public TaxAmountType getAmountType() {
        return amountType;
    }

    @Override
    public double getAmount() {
        return amount;
    }

    @Override
    public String getBypassPermission() {
        return bypassPermission;
    }

    @Override
    public List<TaxReduction> getReductions() {
        return reductions;
    }

    @Override
    public boolean hasItemRules() {
        return itemRulesEnabled && !itemRules.isEmpty();
    }

    @Override
    public List<ItemTaxRule> getItemRules() {
        return itemRules;
    }

    @Override
    public TaxResult calculateSellTax(Player player, BigDecimal price, ItemStack itemStack) {
        if (!enabled) {
            return TaxResult.disabled(price);
        }

        // Check for item-specific rules first
        if (hasItemRules() && itemStack != null) {
            ItemTaxRule matchingRule = findMatchingItemRule(itemStack);
            if (matchingRule != null) {
                TaxType ruleTaxType = matchingRule.getTaxType();
                // Only apply if this rule applies to sell operations
                if (ruleTaxType == TaxType.SELL || ruleTaxType == TaxType.BOTH) {
                    return calculateTax(player, price, matchingRule.getAmountType(), matchingRule.getAmount());
                }
                // Rule doesn't apply to sell, check if default config applies
                if (taxType != TaxType.SELL && taxType != TaxType.BOTH) {
                    return TaxResult.disabled(price);
                }
            }
        }

        // Use default tax configuration
        if (taxType != TaxType.SELL && taxType != TaxType.BOTH) {
            return TaxResult.disabled(price);
        }

        return calculateTax(player, price, amountType, amount);
    }

    @Override
    public TaxResult calculatePurchaseTax(Player player, BigDecimal price, ItemStack itemStack) {
        if (!enabled) {
            return TaxResult.disabled(price);
        }

        // Check for item-specific rules first
        if (hasItemRules() && itemStack != null) {
            ItemTaxRule matchingRule = findMatchingItemRule(itemStack);
            if (matchingRule != null) {
                TaxType ruleTaxType = matchingRule.getTaxType();
                // Only apply if this rule applies to purchase operations
                if (ruleTaxType == TaxType.PURCHASE || ruleTaxType == TaxType.BOTH || ruleTaxType == TaxType.CAPITALISM) {
                    return calculatePurchaseTaxInternal(player, price, matchingRule.getAmountType(),
                            matchingRule.getAmount(), ruleTaxType);
                }
                // Rule doesn't apply to purchase, check if default config applies
                if (taxType != TaxType.PURCHASE && taxType != TaxType.BOTH && taxType != TaxType.CAPITALISM) {
                    return TaxResult.disabled(price);
                }
            }
        }

        // Use default tax configuration
        if (taxType != TaxType.PURCHASE && taxType != TaxType.BOTH && taxType != TaxType.CAPITALISM) {
            return TaxResult.disabled(price);
        }

        return calculatePurchaseTaxInternal(player, price, amountType, amount, taxType);
    }

    private TaxResult calculatePurchaseTaxInternal(Player player, BigDecimal price,
                                                   TaxAmountType calcAmountType, double calcAmount, TaxType calcTaxType) {
        TaxResult baseResult = calculateTax(player, price, calcAmountType, calcAmount);

        if (baseResult.isBypassed() || !baseResult.hasTax()) {
            return baseResult;
        }

        // For CAPITALISM type, the finalPrice is what the buyer pays (price + tax)
        // For PURCHASE/BOTH, finalPrice is what the seller receives (price - tax)
        if (calcTaxType == TaxType.CAPITALISM) {
            BigDecimal buyerPays = price.add(baseResult.taxAmount());
            return new TaxResult(
                    baseResult.taxAmount(),
                    baseResult.taxPercentage(),
                    price,
                    buyerPays,  // Buyer pays more
                    false,
                    baseResult.isReduced(),
                    baseResult.reductionPercentage()
            );
        } else {
            // PURCHASE or BOTH: seller receives less
            BigDecimal sellerReceives = price.subtract(baseResult.taxAmount());
            return new TaxResult(
                    baseResult.taxAmount(),
                    baseResult.taxPercentage(),
                    price,
                    sellerReceives,
                    false,
                    baseResult.isReduced(),
                    baseResult.reductionPercentage()
            );
        }
    }

    /**
     * Calculates tax with bypass and reduction checks.
     */
    private TaxResult calculateTax(Player player, BigDecimal price, TaxAmountType calcAmountType, double calcAmount) {
        // Check for bypass
        if (canBypass(player)) {
            return TaxResult.bypassed(price);
        }

        // Calculate base tax
        BigDecimal taxAmount;
        double effectivePercentage;

        if (calcAmountType == TaxAmountType.PERCENTAGE) {
            effectivePercentage = calcAmount;
            taxAmount = price.multiply(BigDecimal.valueOf(calcAmount / 100.0));
        } else {
            taxAmount = BigDecimal.valueOf(calcAmount);
            // Calculate percentage for display purposes
            effectivePercentage = price.compareTo(BigDecimal.ZERO) > 0
                    ? taxAmount.divide(price, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0;
        }

        // Apply reduction if applicable
        TaxReduction reduction = getReduction(player);
        double reductionPercentage = 0;
        boolean isReduced = false;

        if (reduction != null) {
            reductionPercentage = reduction.percentage();
            isReduced = true;
            taxAmount = taxAmount.multiply(BigDecimal.valueOf(reduction.getMultiplier()));
            effectivePercentage = effectivePercentage * reduction.getMultiplier();
        }

        // Round tax to 2 decimal places
        taxAmount = taxAmount.setScale(2, RoundingMode.HALF_UP);

        BigDecimal finalPrice = price.subtract(taxAmount);

        return new TaxResult(taxAmount, effectivePercentage, price, finalPrice, false, isReduced, reductionPercentage);
    }

    @Override
    public boolean canBypass(Player player) {
        if (bypassPermission == null || bypassPermission.isEmpty()) {
            return false;
        }
        return player.hasPermission(bypassPermission);
    }

    @Override
    public TaxReduction getReduction(Player player) {
        for (TaxReduction reduction : reductions) {
            if (player.hasPermission(reduction.permission())) {
                return reduction;
            }
        }
        return null;
    }

    /**
     * Finds the first matching item rule for the given item.
     */
    private ItemTaxRule findMatchingItemRule(ItemStack itemStack) {
        for (ItemTaxRule rule : itemRules) {
            if (rule.matches(itemStack)) {
                return rule;
            }
        }
        return null;
    }
}
