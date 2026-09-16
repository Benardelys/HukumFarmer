package me.hukumcraft.hukumfarmer.task;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Periodically triggers the asynchronous backup manager.
 */
public class BackupTask extends BukkitRunnable {

    private final HukumFarmer plugin;

    public BackupTask(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getBackupManager().performBackupAsync();
    }
}
