package me.hukumcraft.hukumfarmer.task;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.model.CropConfig;
import me.hukumcraft.hukumfarmer.model.Farmer;
import me.hukumcraft.hukumfarmer.model.FarmerLevel;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import java.util.Map;

/**
 * Highly optimized, periodic Auto-Sell task.
 */
public class AutoSellTask extends BukkitRunnable {

    private final HukumFarmer plugin;
    private boolean enabled = true;
    private double taxRate = 0.0;
    private boolean directDeposit = false;

    public AutoSellTask(HukumFarmer plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        FileConfiguration config = plugin.getConfigManager().getMainConfig();
        this.enabled = config.getBoolean("auto-sell.enabled", true);
        this.taxRate = config.getDouble("auto-sell.tax-rate", 0.0);
        this.directDeposit = config.getBoolean("auto-sell.direct-deposit", false);
    }

    @Override
    public void run() {
        if (!enabled) return;
        if (!plugin.getVaultHook().isHooked()) return;

        Collection<Farmer> farmers = plugin.getFarmerManager().getActiveFarmers();
        if (farmers.isEmpty()) return;

        for (Farmer farmer : farmers) {
            if (!farmer.isAutoSell()) continue;
            if (farmer.getStoredCrops().isEmpty()) continue;

            FarmerLevel level = plugin.getLevelManager().getLevel(farmer.getLevel());
            double totalEarned = 0.0;

            for (Map.Entry<String, Long> entry : farmer.getStoredCrops().entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0) continue;

                CropConfig crop = plugin.getCropManager().getCrop(entry.getKey()).orElse(null);
                if (crop != null && crop.isEnabled() && crop.isAutoSell()) {
                    long count = entry.getValue();
                    double price = count * crop.getSellPrice() * level.getMultiplier();
                    if (taxRate > 0.0) {
                        price *= (1.0 - taxRate);
                    }

                    farmer.removeCrop(crop.getId(), count);
                    totalEarned += price;
                }
            }

            if (totalEarned > 0) {
                if (directDeposit) {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(farmer.getOwnerUUID());
                    plugin.getVaultHook().deposit(player, totalEarned);
                    farmer.setTotalEarnings(farmer.getTotalEarnings() + totalEarned);
                } else {
                    farmer.addEarnings(totalEarned);
                }
                farmer.markDirty();
            }
        }
    }
}
