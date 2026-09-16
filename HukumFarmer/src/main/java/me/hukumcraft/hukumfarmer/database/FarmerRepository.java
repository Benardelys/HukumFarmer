package me.hukumcraft.hukumfarmer.database;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.model.Farmer;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * Repository layer providing thread-safe, non-blocking asynchronous persistence.
 */
public class FarmerRepository {

    private final HukumFarmer plugin;
    private final DatabaseManager databaseManager;
    private final ExecutorService dbExecutor;

    public FarmerRepository(HukumFarmer plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        int threads = Math.max(1, plugin.getConfigManager().getMainConfig().getInt("data-saving.async-save-threads", 2));
        this.dbExecutor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "HukumFarmer-DB-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    public CompletableFuture<Optional<Farmer>> loadFarmerAsync(UUID ownerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return databaseManager.loadFarmer(ownerUUID);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load farmer for UUID: " + ownerUUID, e);
                return Optional.empty();
            }
        }, dbExecutor);
    }

    public CompletableFuture<Collection<Farmer>> loadAllFarmersAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return databaseManager.loadAllFarmers();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load all farmers from database!", e);
                return Collections.emptyList();
            }
        }, dbExecutor);
    }

    public CompletableFuture<Void> saveFarmerAsync(Farmer farmer) {
        if (farmer == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            try {
                databaseManager.saveFarmer(farmer);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to asynchronously save farmer for " + farmer.getOwnerName(), e);
            }
        }, dbExecutor);
    }

    public CompletableFuture<Void> saveFarmersBatchAsync(Collection<Farmer> farmers) {
        if (farmers == null || farmers.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            try {
                databaseManager.saveFarmersBatch(farmers);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to asynchronously save batch of farmers!", e);
            }
        }, dbExecutor);
    }

    public void saveFarmerSync(Farmer farmer) {
        if (farmer == null) return;
        try {
            databaseManager.saveFarmer(farmer);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to synchronously save farmer for " + farmer.getOwnerName(), e);
        }
    }

    public void saveFarmersBatchSync(Collection<Farmer> farmers) {
        if (farmers == null || farmers.isEmpty()) return;
        try {
            databaseManager.saveFarmersBatch(farmers);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to synchronously save batch of farmers!", e);
        }
    }

    public CompletableFuture<Void> deleteFarmerAsync(UUID ownerUUID) {
        return CompletableFuture.runAsync(() -> {
            try {
                databaseManager.deleteFarmer(ownerUUID);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to asynchronously delete farmer for UUID: " + ownerUUID, e);
            }
        }, dbExecutor);
    }

    public CompletableFuture<Void> savePlayerLanguageAsync(UUID playerUUID, String langCode) {
        return CompletableFuture.runAsync(() -> {
            try {
                databaseManager.savePlayerLanguage(playerUUID, langCode);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save player language for UUID: " + playerUUID, e);
            }
        }, dbExecutor);
    }

    public CompletableFuture<Map<UUID, String>> loadAllPlayerLanguagesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return databaseManager.loadAllPlayerLanguages();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load player languages from database!", e);
                return Collections.emptyMap();
            }
        }, dbExecutor);
    }

    public void shutdown() {
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        databaseManager.shutdown();
    }
}
