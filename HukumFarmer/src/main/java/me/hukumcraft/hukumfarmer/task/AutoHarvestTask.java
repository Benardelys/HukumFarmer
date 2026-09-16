package me.hukumcraft.hukumfarmer.task;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.compatibility.CompatibilityUtil;
import me.hukumcraft.hukumfarmer.model.CropConfig;
import me.hukumcraft.hukumfarmer.model.Farmer;
import me.hukumcraft.hukumfarmer.model.FarmerLevel;
import me.hukumcraft.hukumfarmer.util.SoundEffectUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import java.util.Optional;

/**
 * Highly optimized, time-sliced Auto-Harvest task.
 */
public class AutoHarvestTask extends BukkitRunnable {

    private final HukumFarmer plugin;

    public AutoHarvestTask(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        FileConfiguration config = plugin.getConfigManager().getMainConfig();
        if (!config.getBoolean("auto-harvest.enabled", true)) {
            return;
        }

        Collection<Farmer> activeFarmers = plugin.getFarmerManager().getActiveFarmers();
        if (activeFarmers.isEmpty()) return;

        int maxCropsPerCycle = config.getInt("auto-harvest.max-crops-per-cycle", 16);
        boolean replant = config.getBoolean("auto-harvest.replant", true);
        boolean playParticles = config.getBoolean("auto-harvest.particles", true);
        String particleName = config.getString("auto-harvest.particle-type", "VILLAGER_HAPPY");
        boolean playSound = config.getBoolean("auto-harvest.sound", true);
        String soundName = config.getString("auto-harvest.sound-type", "BLOCK_CROP_BREAK");
        float soundVol = (float) config.getDouble("auto-harvest.sound-volume", 0.5);
        float soundPitch = (float) config.getDouble("auto-harvest.sound-pitch", 1.2);

        long now = System.currentTimeMillis();

        for (Farmer farmer : activeFarmers) {
            if (!farmer.isAutoHarvest()) continue;

            Location center = farmer.getLocation();
            if (center == null || center.getWorld() == null) continue;

            World world = center.getWorld();
            int chunkX = center.getBlockX() >> 4;
            int chunkZ = center.getBlockZ() >> 4;

            // Performance: Do not process unloaded chunks
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }

            FarmerLevel level = plugin.getLevelManager().getLevel(farmer.getLevel());
            long harvestIntervalMs = (long) level.getHarvestSpeedSeconds() * 1000L;

            if (now - farmer.getLastHarvestTime() < harvestIntervalMs) {
                continue;
            }

            long currentStorage = farmer.getTotalStoredItems();
            long maxStorage = level.getMaxStorage();
            if (currentStorage >= maxStorage && plugin.getConfigManager().getStorageConfig().getBoolean("storage.stop-harvest-when-full", true)) {
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
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
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
}
