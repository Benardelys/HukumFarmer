package me.hukumcraft.hukumfarmer.hook.smartspawner;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.model.CropConfig;
import me.hukumcraft.hukumfarmer.model.Farmer;
import me.hukumcraft.hukumfarmer.model.FarmerLevel;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * High-level orchestration manager for SmartSpawner and HukumFarmer synergy.
 * Safely handles spawner detection within farmer radius, mob drop collection into farmer storage,
 * and multiplier/XP calculations.
 */
public class SmartSpawnerManager {

    private final HukumFarmer plugin;
    private final SmartSpawnerHook hook;
    private final Set<Material> allowedDropMaterials = new HashSet<>();

    public SmartSpawnerManager(HukumFarmer plugin, SmartSpawnerHook hook) {
        this.plugin = plugin;
        this.hook = hook;
    }

    public void init() {
        reloadConfig();
    }

    public void reloadConfig() {
        allowedDropMaterials.clear();
        FileConfiguration config = plugin.getConfigManager().getMainConfig();
        List<String> list = config.getStringList("integrations.smartspawner.allowed-drops");

        if (list.isEmpty()) {
            // Default common mob drops
            allowedDropMaterials.addAll(Arrays.asList(
                    Material.ROTTEN_FLESH, Material.BONE, Material.GUNPOWDER, Material.STRING,
                    Material.SPIDER_EYE, Material.SLIME_BALL, Material.ENDER_PEARL, Material.BLAZE_ROD,
                    Material.GHAST_TEAR, Material.MAGMA_CREAM, Material.LEATHER, Material.FEATHER,
                    Material.EGG, Material.PORKCHOP, Material.BEEF, Material.CHICKEN, Material.MUTTON
            ));
        } else {
            for (String name : list) {
                try {
                    Material mat = Material.matchMaterial(name.toUpperCase().trim());
                    if (mat != null) {
                        allowedDropMaterials.add(mat);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    public SmartSpawnerHook getHook() {
        return hook;
    }

    public boolean isIntegrationActive() {
        return hook.isAvailable() && plugin.getConfigManager().getMainConfig().getBoolean("integrations.smartspawner.enabled", true);
    }

    /**
     * Finds the count of SmartSpawners located within a Farmer's radius.
     *
     * @param farmer The farmer instance
     * @return Total spawner blocks (including stack multiplier if applicable)
     */
    public int countSpawnersInRadius(Farmer farmer) {
        if (!isIntegrationActive() || farmer == null || farmer.getLocation() == null) {
            return 0;
        }

        Location center = farmer.getLocation();
        World world = center.getWorld();
        if (world == null) return 0;

        FarmerLevel level = plugin.getLevelManager().getLevel(farmer.getLevel());
        int configRadius = plugin.getConfigManager().getMainConfig().getInt("integrations.smartspawner.search-radius", 0);
        int radius = configRadius > 0 ? configRadius : level.getHarvestRadius();

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        int totalSpawners = 0;
        int minY = Math.max(world.getMinHeight(), centerY - 5);
        int maxY = Math.min(world.getMaxHeight() - 1, centerY + 5);

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (hook.isSmartSpawnerBlock(block)) {
                        int stack = hook.getStackAmount(block.getLocation());
                        totalSpawners += Math.max(1, stack);
                    }
                }
            }
        }

        return totalSpawners;
    }

    /**
     * Checks whether an item drop material is allowed to be collected by the farmer.
     */
    public boolean isAllowedDrop(Material material) {
        return material != null && allowedDropMaterials.contains(material);
    }

    /**
     * Processes and deposits a mob drop into the nearest matching Farmer's storage.
     *
     * @param dropLocation Location where the mob died or item dropped
     * @param itemStack The dropped item
     * @return true if the drop was absorbed into farmer storage, false otherwise
     */
    public boolean processSpawnerMobDrop(Location dropLocation, ItemStack itemStack) {
        if (!isIntegrationActive() || dropLocation == null || itemStack == null || itemStack.getAmount() <= 0) {
            return false;
        }

        FileConfiguration config = plugin.getConfigManager().getMainConfig();
        if (!config.getBoolean("integrations.smartspawner.collect-spawner-drops", true)) {
            return false;
        }

        Material mat = itemStack.getType();
        if (!isAllowedDrop(mat)) {
            return false;
        }

        // Find farmer in range
        Farmer nearbyFarmer = findNearestFarmer(dropLocation);
        if (nearbyFarmer == null || !nearbyFarmer.isAutoHarvest()) {
            return false;
        }

        FarmerLevel level = plugin.getLevelManager().getLevel(nearbyFarmer.getLevel());
        long maxStorage = level.getMaxStorage();
        if (nearbyFarmer.getTotalStoredItems() >= maxStorage && plugin.getConfigManager().getStorageConfig().getBoolean("storage.stop-harvest-when-full", true)) {
            return false;
        }

        long amount = itemStack.getAmount();
        if (config.getBoolean("integrations.smartspawner.apply-level-multiplier", true)) {
            amount = Math.max(1, Math.round(amount * level.getMultiplier()));
        }

        String cropKey = mat.name();
        // Check if there is an existing crop registered for this material, or use material key
        Optional<CropConfig> optCrop = plugin.getCropManager().getCropByItem(mat);
        int xpReward = 1;
        if (optCrop.isPresent()) {
            cropKey = optCrop.get().getId();
            xpReward = optCrop.get().getXpReward();
        }

        long added = nearbyFarmer.addCrop(cropKey, amount, maxStorage);
        if (added > 0) {
            if (config.getBoolean("integrations.smartspawner.grant-xp", true)) {
                nearbyFarmer.addXp(xpReward * added);
            }
            nearbyFarmer.markDirty();
            return true;
        }

        return false;
    }

    /**
     * Finds the nearest active Farmer whose radius covers the specified location.
     */
    public Farmer findNearestFarmer(Location location) {
        if (location == null || location.getWorld() == null) return null;

        World world = location.getWorld();
        double bestDistSq = Double.MAX_VALUE;
        Farmer nearest = null;

        for (Farmer farmer : plugin.getFarmerManager().getActiveFarmers()) {
            Location farmerLoc = farmer.getLocation();
            if (farmerLoc == null || !farmerLoc.getWorld().equals(world)) continue;

            FarmerLevel level = plugin.getLevelManager().getLevel(farmer.getLevel());
            double radius = level.getHarvestRadius();
            double distSq = farmerLoc.distanceSquared(location);

            if (distSq <= (radius * radius) && distSq < bestDistSq) {
                bestDistSq = distSq;
                nearest = farmer;
            }
        }

        return nearest;
    }
}
