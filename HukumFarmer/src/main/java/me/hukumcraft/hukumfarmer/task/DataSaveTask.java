package me.hukumcraft.hukumfarmer.task;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Background periodic autosave task for modified farmer data.
 */
public class DataSaveTask extends BukkitRunnable {

    private final HukumFarmer plugin;

    public DataSaveTask(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getFarmerManager().saveDirtyFarmersAsync();
    }
}
