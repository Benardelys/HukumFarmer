package me.hukumcraft.hukumfarmer.compatibility;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Cocoa;

import java.util.Optional;

/**
 * Utility class to ensure smooth compatibility across Paper/Spigot 1.21 up to 1.21.11 and 26.x.
 */
public final class CompatibilityUtil {

    private CompatibilityUtil() {}

    /**
     * Safely resolve a Material by name.
     */
    public static Optional<Material> matchMaterial(String name) {
        if (name == null || name.isEmpty()) {
            return Optional.empty();
        }
        Material material = Material.matchMaterial(name.toUpperCase());
        if (material == null) {
            material = Material.getMaterial(name.toUpperCase());
        }
        return Optional.ofNullable(material);
    }

    /**
     * Checks if a block is a mature crop ready to be harvested.
     */
    public static boolean isMature(Block block) {
        if (block == null) return false;
        BlockData blockData = block.getBlockData();

        if (blockData instanceof Cocoa cocoa) {
            return cocoa.getAge() >= cocoa.getMaximumAge();
        }

        if (blockData instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }

        Material type = block.getType();
        // Sugar Cane & Cactus grow vertically: if the block below is also sugar cane/cactus, this upper block is harvestable
        if (type == Material.SUGAR_CANE || type == Material.CACTUS) {
            Block below = block.getRelative(0, -1, 0);
            return below.getType() == type;
        }

        // Melon & Pumpkin blocks themselves are harvestable
        if (type == Material.MELON || type == Material.PUMPKIN) {
            return true;
        }

        return false;
    }

    /**
     * Safely resets crop age to 0 or harvests block.
     *
     * @param block The block to harvest
     * @param replant Whether to replant or clear the block
     */
    public static void harvestAndReplant(Block block, boolean replant) {
        if (block == null) return;
        BlockData blockData = block.getBlockData();

        if (blockData instanceof Cocoa cocoa) {
            if (replant) {
                cocoa.setAge(0);
                block.setBlockData(cocoa, true);
            } else {
                block.setType(Material.AIR, true);
            }
            return;
        }

        if (blockData instanceof Ageable ageable) {
            if (replant) {
                ageable.setAge(0);
                block.setBlockData(ageable, true);
            } else {
                block.setType(Material.AIR, true);
            }
            return;
        }

        Material type = block.getType();
        if (type == Material.SUGAR_CANE || type == Material.CACTUS) {
            // Break only the upper blocks, leave base block intact
            block.setType(Material.AIR, true);
            return;
        }

        if (type == Material.MELON || type == Material.PUMPKIN) {
            block.setType(Material.AIR, true);
        }
    }
}
