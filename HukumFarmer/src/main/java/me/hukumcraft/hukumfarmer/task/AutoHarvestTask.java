package me.hukumcraft.hukumfarmer.task;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.compatibility.CompatibilityUtil;
import me.hukumcraft.hukumfarmer.model.CropConfig;
import me.hukumcraft.hukumfarmer.model.Farmer;
import me.hukumcraft.hukumfarmer.model.FarmerLevel;
import me.hukumcraft.hukumfarmer.util.SoundEffectUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import java.util.Optional;

/**
 * Highly optimized, chunk-aware, player-proximity aware Auto-Harvest task.
 * Designed to minimize MSPT, CPU cycles, and block queries under high server loads.
 */
public class AutoHarvestTask extends BukkitRunnable {

    private final HukumFarmer plugin;

    // Pre-cached configuration fields to eliminate YAML lookups inside execution loops
    private boolean enabled = true;
    private int maxCropsPerCycle = 16;
    private boolean replant = true;
    private boolean playParticles = true;
    private String particleName = "VILLAGER_HAPPY";
    private boolean playSound = true;
    private String soundName = "BLOCK_CROP_BREAK";
    private float soundVol = 0.5f;
    private float soundPitch = 1.2f;
    private boolean stopHarvestWhenFull = true;
    private boolean playerProximityCheck = true;
    private double proximityRadiusSq = 64.0 * 64.0; // 64 blocks squared

    public AutoHarvestTask(HukumFarmer plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        FileConfiguration config = plugin.getConfigManager().getMainConfig();
        this.enabled = config.getBoolean("auto-harvest.enabled", true);
        this.maxCropsPerCycle = config.getInt("auto-harvest.max-crops-per-cycle", 16);
        this.replant = config.getBoolean("auto-harvest.replant", true);
        this.playParticles = config.getBoolean("auto-harvest.particles", true);
        this.particleName = config.getString("auto-harvest.particle-type", "VILLAGER_HAPPY");
        this.playSound = config.getBoolean("auto-harvest.sound", true);
        this.soundName = config.getString("auto-harvest.sound-type", "BLOCK_CROP_BREAK");
        this.soundVol = (float) config.getDouble("auto-harvest.sound-volume", 0.5);
        this.soundPitch = (float) config.getDouble("auto-harvest.sound-pitch", 1.2);
        this.stopHarvestWhenFull = plugin.getConfigManager().getStorageConfig().getBoolean("storage.stop-harvest-when-full", true);
        this.playerProximityCheck = config.getBoolean("auto-harvest.player-proximity-check", true);
        double proxDist = config.getDouble("auto-harvest.player-proximity-distance", 64.0);
        this.proximityRadiusSq = proxDist * proxDist;
    }

    @Override
    public void run() {
        if (!enabled) return;

        Collection<Farmer> activeFarmers = plugin.getFarmerManager().getActiveFarmers();
        if (activeFarmers.isEmpty()) return;

        long now = System.currentTimeMillis();

        for (Farmer farmer : activeFarmers) {
            if (!farmer.isAutoHarvest()) continue;

            Location center = farmer.getLocation();
            if (center == null) continue;

            World world = center.getWorld();
            if (world == null) continue;

            // 1. Skip if world has zero players online
            if (world.getPlayers().isEmpty()) {
                continue;
            }

            int centerChunkX = center.getBlockX() >> 4;
            int centerChunkZ = center.getBlockZ() >> 4;

            // 2. Performance: Check if farmer's main chunk is loaded
            if (!world.isChunkLoaded(centerChunkX, centerChunkZ)) {
                continue;
            }

            // 3. Player Proximity Optimization: Only process if at least one player is nearby
            if (playerProximityCheck && !isAnyPlayerNearby(world, center)) {
                continue;
            }

            FarmerLevel level = plugin.getLevelManager().getLevel(farmer.getLevel());
            long harvestIntervalMs = (long) level.getHarvestSpeedSeconds() * 1000L;

            if (now - farmer.getLastHarvestTime() < harvestIntervalMs) {
                continue;
            }

            long currentStorage = farmer.getTotalStoredItems();
            long maxStorage = level.getMaxStorage();
            if (currentStorage >= maxStorage && stopHarvestWhenFull) {
                continue;
            }

            int radius = level.getHarvestRadius();
            int centerX = center.getBlockX();
            int centerY = center.getBlockY();
            int centerZ = center.getBlockZ();

            int harvestedCount = 0;
            long earnedXp = 0;

            // Safe bounded 3D bounding box scanning around farmer
            int minY = Math.max(world.getMinHeight(), centerY - 3);
            int maxY = Math.min(world.getMaxHeight() - 1, centerY + 3);

            scanLoop:
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                int blockChunkX = x >> 4;
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    int blockChunkZ = z >> 4;

                    // Do NOT force-load adjacent chunks
                    if (!world.isChunkLoaded(blockChunkX, blockChunkZ)) {
                        continue;
                    }

                    for (int y = minY; y <= maxY; y++) {
                        Block block = world.getBlockAt(x, y, z);
                        Optional<CropConfig> cropOpt = plugin.getCropManager().getCropByBlock(block);

                        if (cropOpt.isPresent()) {
                            CropConfig crop = cropOpt.get();
                            if (!crop.isEnabled() || !crop.isAutoHarvest()) continue;

                            if (CompatibilityUtil.isMature(block)) {
                                CompatibilityUtil.harvestAndReplant(block, replant);

                                long cropToAdd = Math.max(1, Math.round(1 * level.getMultiplier()));
                                long added = farmer.addCrop(crop.getId(), cropToAdd, maxStorage);

                                if (added > 0) {
                                    earnedXp += (crop.getXpReward() * added);
                                    harvestedCount += added;

                                    if (playParticles) {
                                        SoundEffectUtil.spawnParticle(block.getLocation(), particleName, 3);
                                    }
                                    if (playSound) {
                                        SoundEffectUtil.playSound(block.getLocation(), soundName, soundVol, soundPitch);
                                    }
                                }

                                if (harvestedCount >= maxCropsPerCycle) {
                                    break scanLoop;
                                }
                            }
                        }
                    }
                }
            }

            if (harvestedCount > 0) {
                farmer.setLastHarvestTime(now);
                farmer.addXp(earnedXp);
                farmer.markDirty();
            }
        }
    }

    private boolean isAnyPlayerNearby(World world, Location center) {
        double cx = center.getX();
        double cy = center.getY();
        double cz = center.getZ();

        for (Player p : world.getPlayers()) {
            Location pLoc = p.getLocation();
            double dx = pLoc.getX() - cx;
            double dy = pLoc.getY() - cy;
            double dz = pLoc.getZ() - cz;
            double distSq = (dx * dx) + (dy * dy) + (dz * dz);
            if (distSq <= proximityRadiusSq) {
                return true;
            }
        }
        return false;
    }
}
