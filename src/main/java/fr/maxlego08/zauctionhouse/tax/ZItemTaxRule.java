package fr.maxlego08.zauctionhouse.tax;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.tax.ItemTaxRule;
import fr.maxlego08.zauctionhouse.api.tax.TaxAmountType;
import fr.maxlego08.zauctionhouse.api.tax.TaxType;
import fr.maxlego08.zauctionhouse.rule.ZItemRuleContext;
import org.bukkit.inventory.ItemStack;

/**
 * Implementation of {@link ItemTaxRule} that defines tax rules for specific items.
 */
public record ZItemTaxRule(
        String name,
        int priority,
        TaxType taxType,
        TaxAmountType amountType,
        double amount,
        Rule rule
) implements ItemTaxRule {

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getPriority() {
        return priority;
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
    public Rule getRule() {
        return rule;
    }

    @Override
    public boolean matches(ItemStack itemStack) {
        if (rule == null || itemStack == null) {
            return false;
        }
        return rule.matches(new ZItemRuleContext(itemStack));
    }
}
