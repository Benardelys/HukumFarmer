package me.hukumcraft.hukumfarmer.model;

/**
 * Encapsulates the attributes and perks for a specific Farmer level.
 */
public class FarmerLevel {

    private final int level;
    private final long requiredXp;
    private final long maxStorage;
    private final double multiplier;
    private final int harvestSpeedSeconds;
    private final int harvestRadius;
    private final double upgradeCost;
    private final long upgradeXp;

    public FarmerLevel(int level, long requiredXp, long maxStorage, double multiplier,
                       int harvestSpeedSeconds, int harvestRadius, double upgradeCost, long upgradeXp) {
        this.level = level;
        this.requiredXp = requiredXp;
        this.maxStorage = maxStorage;
        this.multiplier = multiplier;
        this.harvestSpeedSeconds = Math.max(1, harvestSpeedSeconds);
        this.harvestRadius = Math.max(1, harvestRadius);
        this.upgradeCost = upgradeCost;
        this.upgradeXp = upgradeXp;
    }

    public int getLevel() {
        return level;
    }

    public long getRequiredXp() {
        return requiredXp;
    }

    public long getMaxStorage() {
        return maxStorage;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public int getHarvestSpeedSeconds() {
        return harvestSpeedSeconds;
    }

    public int getHarvestRadius() {
        return harvestRadius;
    }

    public double getUpgradeCost() {
        return upgradeCost;
    }

    public long getUpgradeXp() {
        return upgradeXp;
    }
}
