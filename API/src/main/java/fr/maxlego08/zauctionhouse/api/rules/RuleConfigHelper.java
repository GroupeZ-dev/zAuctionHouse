package fr.maxlego08.zauctionhouse.api.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper class for extracting values from rule configuration maps.
 * Provides type-safe methods for common configuration value types.
 */
public final class RuleConfigHelper {

    private RuleConfigHelper() {
        // Utility class
    }

    /**
     * Gets a string value from the configuration map.
     *
     * @param map the configuration map
     * @param key the key to look up
     * @return the string value, or null if not present
     */
    public static String getString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * Gets a string value with a default fallback.
     *
     * @param map          the configuration map
     * @param key          the key to look up
     * @param defaultValue the default value if not present
     * @return the string value or default
     */
    public static String getString(Map<?, ?> map, String key, String defaultValue) {
        String value = getString(map, key);
        return value != null ? value : defaultValue;
    }

    /**
     * Gets a boolean value from the configuration map.
     *
     * @param map          the configuration map
     * @param key          the key to look up
     * @param defaultValue the default value if not present
     * @return the boolean value or default
     */
    public static boolean getBoolean(Map<?, ?> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean b) return b;
        if (value != null) return Boolean.parseBoolean(String.valueOf(value));
        return defaultValue;
    }

    /**
     * Gets an integer value from the configuration map.
     *
     * @param map          the configuration map
     * @param key          the key to look up
     * @param defaultValue the default value if not present or invalid
     * @return the integer value or default
     */
    public static int getInt(Map<?, ?> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.intValue();
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    /**
     * Gets a list of strings from the configuration map.
     *
     * @param map the configuration map
     * @param key the key to look up
     * @return list of strings (never null, may be empty)
     */
    public static List<String> getStringList(Map<?, ?> map, String key) {
        Object raw = map.get(key);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object o : list) {
            if (o != null) {
                result.add(String.valueOf(o));
            }
        }
        return result;
    }

    /**
     * Gets a list of integers from the configuration map.
     *
     * @param map the configuration map
     * @param key the key to look up
     * @return list of integers (never null, may be empty)
     */
    public static List<Integer> getIntegerList(Map<?, ?> map, String key) {
        Object raw = map.get(key);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Number n) {
                result.add(n.intValue());
            } else if (o != null) {
                try {
                    result.add(Integer.parseInt(String.valueOf(o)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    /**
     * Gets a list of maps from the configuration map.
     *
     * @param map the configuration map
     * @param key the key to look up
     * @return list of maps (never null, may be empty)
     */
    public static List<Map<?, ?>> getMapList(Map<?, ?> map, String key) {
        Object raw = map.get(key);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                result.add(m);
            }
        }
        return result;
    }
}
