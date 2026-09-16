package me.hukumcraft.hukumfarmer.model;

import org.bukkit.Material;

/**
 * Standard supported crop types in Minecraft 1.21+.
 */
public enum CropType {
    WHEAT(Material.WHEAT, Material.WHEAT, Material.WHEAT_SEEDS),
    CARROTS(Material.CARROT, Material.CARROTS, Material.CARROT),
    POTATOES(Material.POTATO, Material.POTATOES, Material.POTATO),
    BEETROOTS(Material.BEETROOT, Material.BEETROOTS, Material.BEETROOT_SEEDS),
    MELON(Material.MELON_SLICE, Material.MELON, Material.MELON_SEEDS),
    PUMPKIN(Material.PUMPKIN, Material.PUMPKIN, Material.PUMPKIN_SEEDS),
    SUGAR_CANE(Material.SUGAR_CANE, Material.SUGAR_CANE, Material.SUGAR_CANE),
    CACTUS(Material.CACTUS, Material.CACTUS, Material.CACTUS),
    COCOA(Material.COCOA_BEANS, Material.COCOA, Material.COCOA_BEANS),
    NETHER_WART(Material.NETHER_WART, Material.NETHER_WART, Material.NETHER_WART),
    SWEET_BERRIES(Material.SWEET_BERRIES, Material.SWEET_BERRY_BUSH, Material.SWEET_BERRIES);

    private final Material itemMaterial;
    private final Material blockMaterial;
    private final Material seedMaterial;

    CropType(Material itemMaterial, Material blockMaterial, Material seedMaterial) {
        this.itemMaterial = itemMaterial;
        this.blockMaterial = blockMaterial;
        this.seedMaterial = seedMaterial;
    }

    public Material getItemMaterial() {
        return itemMaterial;
    }

    public Material getBlockMaterial() {
        return blockMaterial;
    }

    public Material getSeedMaterial() {
        return seedMaterial;
    }

    public static CropType fromMaterial(Material material) {
        if (material == null) return null;
        for (CropType type : values()) {
            if (type.getItemMaterial() == material || type.getBlockMaterial() == material || type.getSeedMaterial() == material) {
                return type;
            }
        }
        return null;
    }
}
