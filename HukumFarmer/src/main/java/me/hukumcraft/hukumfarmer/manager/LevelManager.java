package me.hukumcraft.hukumfarmer.manager;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.model.FarmerLevel;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages farmer levels, XP requirements, storage upgrades, and formulas.
 */
public class LevelManager {

    private final HukumFarmer plugin;
    private final Map<Integer, FarmerLevel> levels = new HashMap<>();

    private int maxLevel = 100;
    private long baseDynamicXp = 1000;
    private double dynamicXpGrowth = 1.25;
    private long baseDynamicStorage = 1000;
    private long dynamicStoragePerLevel = 1000;
    private double baseDynamicMultiplier = 1.0;
    private double dynamicMultiplierPerLevel = 0.05;
    private int baseDynamicRadius = 6;
    private int dynamicRadiusPer5Levels = 1;

    public LevelManager(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    public void loadLevels() {
        levels.clear();
        FileConfiguration config = plugin.getConfigManager().getLevelsConfig();

        ConfigurationSection dynamicSection = config.getConfigurationSection("dynamic-levels");
        if (dynamicSection != null) {
            maxLevel = dynamicSection.getInt("max-level", 100);
            baseDynamicXp = dynamicSection.getLong("base-xp", 1000);
            dynamicXpGrowth = dynamicSection.getDouble("xp-growth-factor", 1.25);
            baseDynamicStorage = dynamicSection.getLong("base-storage", 1000);
            dynamicStoragePerLevel = dynamicSection.getLong("storage-per-level", 1000);
            baseDynamicMultiplier = dynamicSection.getDouble("base-multiplier", 1.0);
            dynamicMultiplierPerLevel = dynamicSection.getDouble("multiplier-per-level", 0.05);
            baseDynamicRadius = dynamicSection.getInt("base-radius", 6);
            dynamicRadiusPer5Levels = dynamicSection.getInt("radius-per-5-levels", 1);
        }

        ConfigurationSection levelsSection = config.getConfigurationSection("levels");
        if (levelsSection != null) {
            for (String key : levelsSection.getKeys(false)) {
                try {
                    int lvl = Integer.parseInt(key);
                    ConfigurationSection section = levelsSection.getConfigurationSection(key);
                    if (section == null) continue;

                    long reqXp = section.getLong("required-xp", 0);
                    long storage = section.getLong("storage", 1000);
                    double multiplier = section.getDouble("multiplier", 1.0);
                    int harvestSpeed = section.getInt("harvest-speed", 10);
                    int harvestRadius = section.getInt("harvest-radius", 6);
                    double upgradeCost = section.getDouble("upgrade-cost", 1000.0);
                    long upgradeXp = section.getLong("upgrade-xp", 500);

                    FarmerLevel farmerLevel = new FarmerLevel(lvl, reqXp, storage, multiplier, harvestSpeed, harvestRadius, upgradeCost, upgradeXp);
                    levels.put(lvl, farmerLevel);
                } catch (NumberFormatException ignored) {}
            }
        }

        plugin.getLogger().info("Loaded " + levels.size() + " pre-configured farmer levels (Max Level: " + maxLevel + ").");
    }

    public FarmerLevel getLevel(int level) {
        if (level < 1) level = 1;
        if (levels.containsKey(level)) {
            return levels.get(level);
        }

        // Dynamically compute higher levels if not explicitly defined
        long reqXp = (long) (baseDynamicXp * Math.pow(dynamicXpGrowth, level - 1));
        long storage = baseDynamicStorage + ((long) (level - 1) * dynamicStoragePerLevel);
        double multiplier = baseDynamicMultiplier + ((level - 1) * dynamicMultiplierPerLevel);
        int harvestSpeed = Math.max(1, 10 - (level / 10));
        int harvestRadius = Math.min(20, baseDynamicRadius + ((level - 1) / 5) * dynamicRadiusPer5Levels);
        double upgradeCost = level * 25000.0;
        long upgradeXp = reqXp;

        FarmerLevel dynamicLevel = new FarmerLevel(level, reqXp, storage, multiplier, harvestSpeed, harvestRadius, upgradeCost, upgradeXp);
        levels.put(level, dynamicLevel);
        return dynamicLevel;
    }

    public boolean isMaxLevel(int level) {
        return level >= maxLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }
}
