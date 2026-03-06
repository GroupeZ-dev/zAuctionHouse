package fr.maxlego08.zauctionhouse.utils.tags;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TagRegistry {

    private static final Logger LOGGER = Logger.getLogger(TagRegistry.class.getName());
    private static final Map<String, Tag<Material>> tagMap = new HashMap<>();

    static {
        for (Field field : Tag.class.getDeclaredFields()) {
            if (Tag.class.isAssignableFrom(field.getType())) {
                try {
                    Class<?> genericType = (Class<?>) ((java.lang.reflect.ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                    if (Material.class.isAssignableFrom(genericType)) {
                        register(field.getName(), (Tag<Material>) field.get(null));
                    }
                } catch (Exception exception) {
                    LOGGER.log(Level.WARNING, "Failed to register tag " + field.getName(), exception);
                }
            }
        }

        // Custom tags
        register("blocks", new BlocksTag());
    }

    public static void register(String key, Tag<Material> tag) {
        tagMap.put(key, tag);
    }

    public static Tag<Material> getTag(String key) {
        return tagMap.get(key);
    }
}