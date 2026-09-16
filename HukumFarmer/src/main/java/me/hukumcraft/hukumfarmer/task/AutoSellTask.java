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
 * Periodically processes automatic sales for farmers with auto-sell enabled.
 */
public class AutoSellTask extends BukkitRunnable {

    private final HukumFarmer plugin;

    public AutoSellTask(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        FileConfiguration config = plugin.getConfigManager().getMainConfig();
        if (!config.getBoolean("auto-sell.enabled", true)) {
            return;
        }

        if (!plugin.getVaultHook().isHooked()) {
            return;
        }

        Collection<Farmer> farmers = plugin.getFarmerManager().getActiveFarmers();
        if (farmers.isEmpty()) return;

        double taxRate = config.getDouble("auto-sell.tax-rate", 0.0);
        boolean directDeposit = config.getBoolean("auto-sell.direct-deposit", false);

        for (Farmer farmer : farmers) {
            if (!farmer.isAutoSell()) continue;

            FarmerLevel level = plugin.getLevelManager().getLevel(farmer.getLevel());
            double totalEarned = 0.0;

            for (Map.Entry<String, Long> entry : farmer.getStoredCrops().entrySet()) {
                CropConfig crop = plugin.getCropManager().getCrop(entry.getKey()).orElse(null);
                if (crop != null && crop.isEnabled() && crop.isAutoSell() && entry.getValue() > 0) {
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
