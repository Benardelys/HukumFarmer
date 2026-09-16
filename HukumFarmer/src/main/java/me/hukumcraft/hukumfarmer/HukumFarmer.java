package me.hukumcraft.hukumfarmer;

import me.hukumcraft.hukumfarmer.command.FarmerAdminCommand;
import me.hukumcraft.hukumfarmer.command.FarmerCommand;
import me.hukumcraft.hukumfarmer.command.FarmerNpcCommand;
import me.hukumcraft.hukumfarmer.command.FarmerTabCompleter;
import me.hukumcraft.hukumfarmer.config.ConfigManager;
import me.hukumcraft.hukumfarmer.database.DatabaseManager;
import me.hukumcraft.hukumfarmer.database.FarmerRepository;
import me.hukumcraft.hukumfarmer.database.MySQLDatabase;
import me.hukumcraft.hukumfarmer.database.SQLiteDatabase;
import me.hukumcraft.hukumfarmer.gui.*;
import me.hukumcraft.hukumfarmer.hook.VaultHook;
import me.hukumcraft.hukumfarmer.hook.WorldGuardHook;
import me.hukumcraft.hukumfarmer.hook.axminions.AxMinionsHook;
import me.hukumcraft.hukumfarmer.hook.axminions.AxMinionsManager;
import me.hukumcraft.hukumfarmer.language.LanguageManager;
import me.hukumcraft.hukumfarmer.listener.FarmerBlockListener;
import me.hukumcraft.hukumfarmer.listener.FarmerEggListener;
import me.hukumcraft.hukumfarmer.listener.InventoryListener;
import me.hukumcraft.hukumfarmer.listener.NpcListener;
import me.hukumcraft.hukumfarmer.listener.PlayerListener;
import me.hukumcraft.hukumfarmer.manager.CropManager;
import me.hukumcraft.hukumfarmer.manager.FarmerItemManager;
import me.hukumcraft.hukumfarmer.manager.FarmerManager;
import me.hukumcraft.hukumfarmer.manager.LevelManager;
import me.hukumcraft.hukumfarmer.manager.NpcManager;
import me.hukumcraft.hukumfarmer.message.MessageManager;
import me.hukumcraft.hukumfarmer.security.BackupManager;
import me.hukumcraft.hukumfarmer.security.SecurityManager;
import me.hukumcraft.hukumfarmer.task.AutoHarvestTask;
import me.hukumcraft.hukumfarmer.task.AutoSellTask;
import me.hukumcraft.hukumfarmer.task.BackupTask;
import me.hukumcraft.hukumfarmer.task.DataSaveTask;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

/**
 * HukumFarmer - Advanced Production-Ready Minecraft Farming Plugin
 * Compatible with Paper / Spigot 1.21 - 1.21.11 / 26.x
 */
public class HukumFarmer extends JavaPlugin {

    private static HukumFarmer instance;

    private ConfigManager configManager;
    private LanguageManager languageManager;
    private MessageManager messageManager;
    private SecurityManager securityManager;
    private BackupManager backupManager;
    private DatabaseManager databaseManager;
    private FarmerRepository farmerRepository;
    private VaultHook vaultHook;
    private WorldGuardHook worldGuardHook;
    private AxMinionsHook axMinionsHook;
    private AxMinionsManager axMinionsManager;
    private me.hukumcraft.hukumfarmer.hook.itemsadder.ItemsAdderHook itemsAdderHook;
    private FarmerItemManager farmerItemManager;
    private CropManager cropManager;
    private LevelManager levelManager;
    private FarmerManager farmerManager;
    private NpcManager npcManager;

    private MainMenuGui mainMenuGui;
    private StorageGui storageGui;
    private UpgradeGui upgradeGui;
    private SettingsGui settingsGui;
    private PurchaseGui purchaseGui;
    private LanguageGui languageGui;

    private AutoHarvestTask autoHarvestTask;
    private AutoSellTask autoSellTask;
    private DataSaveTask dataSaveTask;
    private BackupTask backupTask;

    @Override
    public void onEnable() {
        instance = this;
        printStartupBanner();

        // 1. Load Configurations & Languages
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfigs();

        this.languageManager = new LanguageManager(this);
        this.languageManager.loadLanguages();

        this.messageManager = new MessageManager(this, languageManager);

        // 2. Initialize Security & Backup Managers
        this.securityManager = new SecurityManager(this);
        this.backupManager = new BackupManager(this);
        this.backupManager.init();

        // 3. Initialize Hooks (Vault, WorldGuard, AxMinions & ItemsAdder)
        this.vaultHook = new VaultHook(this);
        this.vaultHook.init();

        this.worldGuardHook = new WorldGuardHook(this);

        this.axMinionsHook = new AxMinionsHook(this);
        this.axMinionsHook.init();
        this.axMinionsManager = new AxMinionsManager(this, axMinionsHook);
        this.axMinionsManager.init();

        this.itemsAdderHook = new me.hukumcraft.hukumfarmer.hook.itemsadder.ItemsAdderHook(this);
        this.itemsAdderHook.init();

        // 4. Initialize Database
        if (!initDatabase()) {
            getLogger().severe("Failed to initialize database connection! Disabling plugin...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Pre-cache stored player languages from database
        this.farmerRepository.loadAllPlayerLanguagesAsync().thenAccept(map -> {
            map.forEach(languageManager::setPlayerLanguage);
        });

        // 5. Initialize NPC System (must be ready before FarmerManager restores NPCs)
        this.npcManager = new NpcManager(this);
        this.npcManager.init();

        // 6. Initialize Core Managers
        this.farmerItemManager = new FarmerItemManager(this);

        this.cropManager = new CropManager(this);
        this.cropManager.loadCrops();

        this.levelManager = new LevelManager(this);
        this.levelManager.loadLevels();

        this.farmerManager = new FarmerManager(this, farmerRepository);
        this.farmerManager.loadAll();

        // 7. Initialize GUIs
        this.mainMenuGui = new MainMenuGui(this);
        this.storageGui = new StorageGui(this);
        this.upgradeGui = new UpgradeGui(this);
        this.settingsGui = new SettingsGui(this);
        this.purchaseGui = new PurchaseGui(this);
        this.languageGui = new LanguageGui(this);

        // 8. Register Event Listeners
        registerListeners();

        // 9. Register Commands & Tab Completers
        registerCommands();

        // 10. Schedule Background Tasks
        startTasks();

        getLogger().info("HukumFarmer has been successfully enabled and is ready for production!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down HukumFarmer and saving all player data...");

        // 1. Cancel Tasks
        if (autoHarvestTask != null) autoHarvestTask.cancel();
        if (autoSellTask != null) autoSellTask.cancel();
        if (dataSaveTask != null) dataSaveTask.cancel();
        if (backupTask != null) backupTask.cancel();

        // 2. Clean NPC entities
        if (npcManager != null) {
            npcManager.cleanupRuntimeEntities();
        }

        // 3. Synchronously save all farmers
        if (farmerManager != null) {
            farmerManager.saveAllSync();
        }

        // 4. Close database pools
        if (farmerRepository != null) {
            farmerRepository.shutdown();
        }

        getLogger().info("====================================");
        getLogger().info("        HukumFarmer Disabled        ");
        getLogger().info("====================================");
    }

    public void reload() {
        configManager.reload();
        languageManager.loadLanguages();
        cropManager.loadCrops();
        levelManager.loadLevels();

        if (npcManager != null) {
            npcManager.loadAll();
        }

        if (farmerManager != null) {
            farmerManager.auditNpcReconciliation();
        }

        if (autoHarvestTask != null) autoHarvestTask.cancel();
        if (autoSellTask != null) autoSellTask.cancel();
        if (dataSaveTask != null) dataSaveTask.cancel();
        if (backupTask != null) backupTask.cancel();
        startTasks();

        getLogger().info("HukumFarmer configurations, languages, and NPCs reloaded successfully.");
    }

    private boolean initDatabase() {
        String dbType = configManager.getMainConfig().getString("database.type", "SQLITE").toUpperCase();
        try {
            if ("MYSQL".equals(dbType)) {
                this.databaseManager = new MySQLDatabase(this);
            } else {
                this.databaseManager = new SQLiteDatabase(this);
            }
            this.databaseManager.init();
            this.farmerRepository = new FarmerRepository(this, databaseManager);
            getLogger().info("Database persistence initialized (" + dbType + ").");
            return true;
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "SQL Exception while initializing database backend (" + dbType + ")!", e);
            return false;
        }
    }

    private void registerListeners() {
        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new InventoryListener(this, mainMenuGui, storageGui, upgradeGui, settingsGui, purchaseGui, languageGui), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new FarmerBlockListener(this, mainMenuGui), this);
        pm.registerEvents(new NpcListener(this, mainMenuGui, purchaseGui), this);
        pm.registerEvents(new FarmerEggListener(this), this);
    }

    private void registerCommands() {
        FarmerAdminCommand adminCmd = new FarmerAdminCommand(this);
        FarmerNpcCommand npcCmd = new FarmerNpcCommand(this);
        FarmerCommand mainCmd = new FarmerCommand(this, mainMenuGui, storageGui, upgradeGui, settingsGui, purchaseGui, languageGui, adminCmd, npcCmd);
        FarmerTabCompleter tabCompleter = new FarmerTabCompleter();

        PluginCommand command = getCommand("farmer");
        if (command != null) {
            command.setExecutor(mainCmd);
            command.setTabCompleter(tabCompleter);
        }
    }

    private void startTasks() {
        long harvestInterval = configManager.getMainConfig().getLong("auto-harvest.interval", 40);
        long sellInterval = configManager.getMainConfig().getLong("auto-sell.interval", 600);
        long saveInterval = configManager.getMainConfig().getLong("data-saving.autosave-interval", 6000);
        long backupIntervalSec = configManager.getMainConfig().getLong("backup.interval", 3600);

        this.autoHarvestTask = new AutoHarvestTask(this);
        this.autoHarvestTask.runTaskTimer(this, 100L, Math.max(10L, harvestInterval));

        this.autoSellTask = new AutoSellTask(this);
        this.autoSellTask.runTaskTimer(this, 200L, Math.max(20L, sellInterval));

        this.dataSaveTask = new DataSaveTask(this);
        this.dataSaveTask.runTaskTimerAsynchronously(this, saveInterval, Math.max(1200L, saveInterval));

        if (configManager.getMainConfig().getBoolean("backup.enabled", true)) {
            long backupTicks = Math.max(1200L, backupIntervalSec * 20L);
            this.backupTask = new BackupTask(this);
            this.backupTask.runTaskTimerAsynchronously(this, backupTicks, backupTicks);
        }
    }

    private void printStartupBanner() {
        getLogger().info("====================================");
        getLogger().info("        HukumFarmer                 ");
        getLogger().info("        Version: 1.0.0              ");
        getLogger().info("        Author:  Ardelys            ");
        getLogger().info("        Status:  Enabled            ");
        getLogger().info("====================================");
    }

    public static HukumFarmer getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public SecurityManager getSecurityManager() {
        return securityManager;
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public FarmerRepository getFarmerRepository() {
        return farmerRepository;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public WorldGuardHook getWorldGuardHook() {
        return worldGuardHook;
    }

    public AxMinionsHook getAxMinionsHook() {
        return axMinionsHook;
    }

    public AxMinionsManager getAxMinionsManager() {
        return axMinionsManager;
    }

    public me.hukumcraft.hukumfarmer.hook.itemsadder.ItemsAdderHook getItemsAdderHook() {
        return itemsAdderHook;
    }

    public FarmerItemManager getFarmerItemManager() {
        return farmerItemManager;
    }

    public CropManager getCropManager() {
        return cropManager;
    }

    public LevelManager getLevelManager() {
        return levelManager;
    }

    public FarmerManager getFarmerManager() {
        return farmerManager;
    }

    public NpcManager getNpcManager() {
        return npcManager;
    }

    public MainMenuGui getMainMenuGui() {
        return mainMenuGui;
    }

    public StorageGui getStorageGui() {
        return storageGui;
    }

    public UpgradeGui getUpgradeGui() {
        return upgradeGui;
    }

    public SettingsGui getSettingsGui() {
        return settingsGui;
    }

    public PurchaseGui getPurchaseGui() {
        return purchaseGui;
    }

    public LanguageGui getLanguageGui() {
        return languageGui;
    }
}
