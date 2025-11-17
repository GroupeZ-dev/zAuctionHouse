package fr.maxlego08.zauctionhouse.utils.commands;

import fr.maxlego08.zauctionhouse.utils.ZUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Abstract class providing utility methods for parsing command arguments.
 * Extends {@link ZUtils}.
 */
public abstract class Arguments extends ZUtils {

    protected String[] args;
    protected int parentCount = 0;

    /**
     * Parses the argument at the specified index as a String.
     *
     * @param index the argument index.
     * @return the argument as a String, or null if not present.
     */
    protected String argAsString(int index) {
        try {
            return this.args[index + this.parentCount];
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parses the argument at the specified index as a String, with a default value.
     *
     * @param index        the argument index.
     * @param defaultValue the default value if the argument is not present.
     * @return the argument as a String, or the default value if not present.
     */
    protected String argAsString(int index, String defaultValue) {
        try {
            return this.args[index + this.parentCount];
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Parses the argument at the specified index as a boolean.
     *
     * @param index the argument index.
     * @return the argument as a boolean.
     */
    protected boolean argAsBoolean(int index) {
        return Boolean.valueOf(argAsString(index));
    }

    /**
     * Parses the argument at the specified index as a boolean, with a default value.
     *
     * @param index        the argument index.
     * @param defaultValue the default value if the argument is not present.
     * @return the argument as a boolean, or the default value if not present.
     */
    protected boolean argAsBoolean(int index, boolean defaultValue) {
        try {
            return Boolean.valueOf(argAsString(index));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Parses the argument at the specified index as an integer.
     *
     * @param index the argument index.
     * @return the argument as an integer.
     */
    protected int argAsInteger(int index) {
        return Integer.valueOf(argAsString(index));
    }

    /**
     * Parses the argument at the specified index as an integer, with a default value.
     *
     * @param index        the argument index.
     * @param defaultValue the default value if the argument is not present.
     * @return the argument as an integer, or the default value if not present.
     */
    protected int argAsInteger(int index, int defaultValue) {
        try {
            return Integer.valueOf(argAsString(index));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Parses the argument at the specified index as a long.
     *
     * @param index the argument index.
     * @return the argument as a long.
     */
    protected long argAsLong(int index) {
        return Long.valueOf(argAsString(index));
    }

    /**
     * Parses the argument at the specified index as a long, with a default value.
     *
     * @param index        the argument index.
     * @param defaultValue the default value if the argument is not present.
     * @return the argument as a long, or the default value if not present.
     */
    protected long argAsLong(int index, long defaultValue) {
        try {
            return Long.valueOf(argAsString(index));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Parses the argument at the specified index as a double.
     *
     * @param index the argument index.
     * @return the argument as a double.
     */
    protected double argAsDouble(int index) {
        return Double.valueOf(argAsString(index).replace(",", "."));
    }

    /**
     * Parses the argument at the specified index as a double, with a default value.
     *
     * @param index        the argument index.
     * @param defaultValue the default value if the argument is not present.
     * @return the argument as a double, or the default value if not present.
     */
    protected double argAsDouble(int index, double defaultValue) {
        try {
            return Double.valueOf(argAsString(index).replace(",", "."));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Parses the argument at the specified index as a {@link Player}.
     *
     * @param index the argument index.
     * @return the argument as a Player, or null if not present.
     */
    protected Player argAsPlayer(int index) {
        return Bukkit.getPlayer(argAsString(index));
    }

    /**
     * Parses the argument at the specified index as a {@link Player}, with a default value.
     *
     * @param index        the argument index.
     * @param defaultValue the default value if the argument is not present.
     * @return the argument as a Player, or the default value if not present.
     */
    protected Player argAsPlayer(int index, Player defaultValue) {
        try {
            return Bukkit.getPlayer(argAsString(index));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Parses the argument at the specified index as an {@link OfflinePlayer}.
     *
     * @param index the argument index.
     * @return the argument as an OfflinePlayer.
     */
    protected OfflinePlayer argAsOfflinePlayer(int index) {
        return Bukkit.getOfflinePlayer(argAsString(index));
    }

    /**
     * Parses the argument at the specified index as an {@link OfflinePlayer}, with a default value.
     *
     * @param index        the argument index.
     * @param defaultValue the default value if the argument is not present.
     * @return the argument as an OfflinePlayer, or the default value if not present.
     */
    protected OfflinePlayer argAsOfflinePlayer(int index, OfflinePlayer defaultValue) {
        try {
            return Bukkit.getOfflinePlayer(argAsString(index));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Parses the argument at the specified index as an {@link EntityType}.
     *
     * @param index the argument index.
     * @return the argument as an EntityType.
     */
    protected EntityType argAsEntityType(int index) {
        return EntityType.valueOf(argAsString(index).toUpperCase());
    }

    /**
     * Parses the argument at the specified index as an {@link EntityType}, with a default value.
     *
     * @param index        the argument index.
     * @param defaultValue the default value if the argument is not present.
     * @return the argument as an EntityType, or the default value if not present.
     */
    protected EntityType argAsEntityType(int index, EntityType defaultValue) {
        try {
            return EntityType.valueOf(argAsString(index).toUpperCase());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Parses the argument at the specified index as a {@link World}.
     *
     * @param index the argument index.
     * @return the argument as a World, or null if not present.
     */
    protected World argAsWorld(int index) {
        try {
            return Bukkit.getWorld(argAsString(index));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parses the argument at the specified index as a {@link World}, with a default value.
     *
     * @param index the argument index.
     * @param world the default world if the argument is not present.
     * @return the argument as a World, or the default value if not present.
     */
    protected World argAsWorld(int index, World world) {
        try {
            return Bukkit.getWorld(argAsString(index));
        } catch (Exception e) {
            return world;
        }
    }
}
