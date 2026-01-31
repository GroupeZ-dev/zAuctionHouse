package fr.maxlego08.zauctionhouse.utils.tags;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class BlocksTag implements Tag<Material> {

    @Override
    public boolean isTagged(@NonNull Material material) {
        return material.isBlock();
    }

    @Override
    public @NotNull Set<Material> getValues() {
        return Arrays.stream(Material.values()).filter(material -> material.isBlock() && !material.isLegacy()).collect(Collectors.toSet());
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return new NamespacedKey("zauctionhouse", "blocks");
    }
}
