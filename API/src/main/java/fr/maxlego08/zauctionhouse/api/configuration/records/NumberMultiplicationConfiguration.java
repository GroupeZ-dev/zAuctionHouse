package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.menu.api.utils.TypedMapAccessor;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record NumberMultiplicationConfiguration(boolean enable, Map<String, BigDecimal> multiplications) {

    private static final Pattern PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*([a-z]?)");

    public static NumberMultiplicationConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {

        var enable = configuration.getBoolean("number-sell-multiplication.enable");
        var multiplications = new HashMap<String, BigDecimal>();
        if (enable) {
            var maps = configuration.getMapList("number-sell-multiplication.formats");
            for (Map<?, ?> elements : maps) {
                TypedMapAccessor accessor = new TypedMapAccessor((Map<String, Object>) elements);
                var key = accessor.getString("format");
                var multiplication = accessor.getString("multiplication");
                if (key == null) {
                    plugin.getLogger().info("The format key is null !, you need to fix that !");
                    continue;
                }

                if (multiplication == null) {
                    plugin.getLogger().info("The multiplication key is null !, you need to fix that !");
                    continue;
                }

                multiplications.put(key, new BigDecimal(multiplication));
            }
        }

        return new NumberMultiplicationConfiguration(enable, multiplications);
    }

    public BigDecimal parseNumber(String input) {

        if (input == null || input.isBlank()) return BigDecimal.ZERO;

        input = input.trim().toLowerCase().replace(",", ".");
        Matcher matcher = PATTERN.matcher(input);

        if (!matcher.matches()) return null;

        BigDecimal number = new BigDecimal(matcher.group(1));
        String suffix = matcher.group(2).toUpperCase();

        if (this.multiplications.containsKey(suffix)) {

            BigDecimal multiplier = this.multiplications.get(suffix);
            return number.multiply(multiplier);
        }

        return number;
    }
}
