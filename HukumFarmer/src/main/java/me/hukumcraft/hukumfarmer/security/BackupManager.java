package me.hukumcraft.hukumfarmer.security;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import org.bukkit.Bukkit;

import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Non-blocking asynchronous backup manager for SQLite database and configuration files.
 */
public class BackupManager {

    private final HukumFarmer plugin;
    private final File backupDir;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    public BackupManager(HukumFarmer plugin) {
        this.plugin = plugin;
        this.backupDir = new File(plugin.getDataFolder(), "backups");
    }

    public void init() {
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
    }

    public CompletableFuture<Boolean> performBackupAsync() {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("backup.enabled", true)) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!backupDir.exists()) {
                    backupDir.mkdirs();
                }

                String timestamp = dateFormat.format(new Date());
                File zipFile = new File(backupDir, "backup-" + timestamp + ".zip");

                List<File> filesToBackup = new ArrayList<>();
                File dataFolder = plugin.getDataFolder();

                File dbFile = new File(dataFolder, "database.db");
                if (dbFile.exists()) filesToBackup.add(dbFile);

                File npcFile = new File(dataFolder, "npc.yml");
                if (npcFile.exists()) filesToBackup.add(npcFile);

                File configFile = new File(dataFolder, "config.yml");
                if (configFile.exists()) filesToBackup.add(configFile);

                if (filesToBackup.isEmpty()) {
                    return false;
                }

                try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                    byte[] buffer = new byte[4096];
                    for (File file : filesToBackup) {
                        try (FileInputStream fis = new FileInputStream(file)) {
                            ZipEntry zipEntry = new ZipEntry(file.getName());
                            zos.putNextEntry(zipEntry);

                            int length;
                            while ((length = fis.read(buffer)) > 0) {
                                zos.write(buffer, 0, length);
                            }
                            zos.closeEntry();
                        }
                    }
                }

                rotateBackups();
                plugin.getLogger().info("Successfully created asynchronous backup: " + zipFile.getName());
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create asynchronous data backup!", e);
                return false;
            }
        });
    }

    private void rotateBackups() {
        int keep = plugin.getConfigManager().getMainConfig().getInt("backup.keep-files", 5);
        File[] backups = backupDir.listFiles((dir, name) -> name.endsWith(".zip"));
        if (backups != null && backups.length > keep) {
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
            int toDelete = backups.length - keep;
            for (int i = 0; i < toDelete; i++) {
                try {
                    Files.deleteIfExists(backups[i].toPath());
                } catch (IOException ignored) {}
            }
        }
    }
}
