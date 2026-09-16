package me.hukumcraft.hukumfarmer.model;

import org.bukkit.Material;

/**
 * Encapsulates the configuration attributes for a single crop defined in crops.yml.
 */
public class CropConfig {

    private final String id;
    private final boolean enabled;
    private final Material itemMaterial;
    private final Material blockMaterial;
    private final String displayName;
    private final double sellPrice;
    private final int xpReward;
    private final boolean autoHarvest;
    private final boolean autoSell;
    private final int guiSlot;

    public CropConfig(String id, boolean enabled, Material itemMaterial, Material blockMaterial,
                      String displayName, double sellPrice, int xpReward,
                      boolean autoHarvest, boolean autoSell, int guiSlot) {
        this.id = id;
        this.enabled = enabled;
        this.itemMaterial = itemMaterial;
        this.blockMaterial = blockMaterial;
        this.displayName = displayName;
        this.sellPrice = sellPrice;
        this.xpReward = xpReward;
        this.autoHarvest = autoHarvest;
        this.autoSell = autoSell;
        this.guiSlot = guiSlot;
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Material getItemMaterial() {
        return itemMaterial;
    }

    public Material getBlockMaterial() {
        return blockMaterial;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getSellPrice() {
        return sellPrice;
    }

    public int getXpReward() {
        return xpReward;
    }

    public boolean isAutoHarvest() {
        return autoHarvest;
    }

    public boolean isAutoSell() {
        return autoSell;
    }

    public int getGuiSlot() {
        return guiSlot;
    }
}
