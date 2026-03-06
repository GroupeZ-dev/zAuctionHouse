package fr.maxlego08.zauctionhouse.utils;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permissible;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public abstract class ZUtils extends MessageUtils {

    protected Object createInstanceFromMap(Logger logger, Constructor<?> constructor, Map<?, ?> map) {
        try {
            Object[] arguments = new Object[constructor.getParameterCount()];
            java.lang.reflect.Parameter[] parameters = constructor.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                Class<?> paramType = parameters[i].getType();

                String paramName = parameters[i].getName();
                String configKey = paramName.replaceAll("([A-Z])", "-$1").toLowerCase();

                Object value = map.containsKey(paramName) ? map.get(paramName) : map.get(configKey);
                if (value == null && Number.class.isAssignableFrom(paramType)) {
                    value = 0;
                }
                if (value == null && Boolean.class.isAssignableFrom(paramType)) {
                    value = false;
                }

                if (value != null) {
                    try {
                        if (paramType.isArray()) {
                            Class<?> componentType = paramType.getComponentType();
                            List<?> list = (List<?>) value;
                            Object array = Array.newInstance(componentType, list.size());
                            for (int j = 0; j < list.size(); j++) {
                                Object element = list.get(j);
                                element = convertToRequiredType(logger, element, componentType);
                                Array.set(array, j, element);
                            }
                            value = array;
                        } else {
                            value = convertToRequiredType(logger, value, paramType);
                        }
                    } catch (Exception exception) {
                        logger.log(Level.SEVERE, String.format("Error converting value '%s' for parameter '%s' to type '%s'", value, paramName, paramType.getName()), exception);
                    }
                }

                arguments[i] = value;
            }
            return constructor.newInstance(arguments);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, String.format("Failed to create instance from map with constructor %s", constructor), exception);
            logger.log(Level.SEVERE, String.format("Constructor parameters: %s", (Object) constructor.getParameters()));
            logger.log(Level.SEVERE, String.format("Map content: %s", map));
            throw new RuntimeException("Failed to create instance from map with constructor " + constructor, exception);
        }
    }

    protected Object convertToRequiredType(Logger logger, Object value, Class<?> type) {
        if (value == null) {
            if (type == Integer.class || type == int.class) {
                return 0;
            } else if (type == Double.class || type == double.class) {
                return 0.0;
            } else if (type == Long.class || type == long.class) {
                return 0L;
            } else if (type == Float.class || type == float.class) {
                return 0f;
            } else if (type == Boolean.class || type == boolean.class) {
                return false;
            }
            return null;
        } else if (type.isEnum()) {
            try {
                return Enum.valueOf((Class<Enum>) type, (String) value);
            } catch (IllegalArgumentException exception) {
                logger.log(Level.SEVERE, String.format("Failed to convert '%s' to enum type '%s'", value, type.getName()), exception);
            }
        } else if (type == BigDecimal.class) {
            try {
                return new BigDecimal(value.toString());
            } catch (NumberFormatException exception) {
                logger.log(Level.SEVERE, String.format("Failed to convert '%s' to BigDecimal", value), exception);
            }
        } else if (type == UUID.class) {
            try {
                return UUID.fromString((String) value);
            } catch (IllegalArgumentException exception) {
                logger.log(Level.SEVERE, String.format("Failed to convert '%s' to UUID", value), exception);
            }
        } else if (type == Integer.class || type == int.class) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                logger.log(Level.SEVERE, String.format("Failed to convert '%s' to Integer", value), e);
                throw e;
            }
        } else if (type == Double.class || type == double.class) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException exception) {
                logger.log(Level.SEVERE, String.format("Failed to convert '%s' to Double", value), exception);
            }
        } else if (type == Long.class || type == long.class) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException exception) {
                logger.log(Level.SEVERE, String.format("Failed to convert '%s' to Long", value), exception);
            }
        } else if (type == Boolean.class || type == boolean.class) {
            try {
                return Boolean.parseBoolean(value.toString());
            } catch (Exception exception) {
                logger.log(Level.SEVERE, String.format("Failed to convert '%s' to Boolean", value), exception);
            }
        } else if (type == Float.class || type == float.class) {
            try {
                return Float.parseFloat(value.toString());
            } catch (NumberFormatException exception) {
                logger.log(Level.SEVERE, String.format("Failed to convert '%s' to Float", value), exception);
            }
        }
        return value;
    }

    /**
     * Checks if a permissible entity has a specific permission.
     *
     * @param permissible the entity to check.
     * @param permission  the permission string to check for.
     * @return true if the entity has the permission, false otherwise.
     */
    protected boolean hasPermission(Permissible permissible, String permission) {
        return permissible.hasPermission(permission);
    }

    protected void files(File folder, Consumer<File> consumer) {
        try (Stream<Path> s = Files.walk(Paths.get(folder.getPath()))) {
            s.skip(1).map(Path::toFile).filter(File::isFile).filter(e -> e.getName().endsWith(".yml")).forEach(consumer);
        } catch (IOException exception) {
            Logger.getLogger(ZUtils.class.getName()).log(Level.SEVERE, "Failed to walk folder " + folder.getPath(), exception);
        }
    }

    protected void removeItemInHand(Player player, int how) {
        var inventory = player.getInventory();
        if (inventory.getItemInMainHand().getAmount() > how) {
            inventory.getItemInMainHand().setAmount(inventory.getItemInMainHand().getAmount() - how);
        } else {
            inventory.setItemInMainHand(new ItemStack(Material.AIR));
        }
        player.updateInventory();
    }
}
