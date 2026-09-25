package me.hukumcraft.hukumfarmer.hook.smartspawner;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Robust, fault-tolerant integration hook for SmartSpawner.
 * Designed with dynamic reflection and PDC fallbacks to ensure HukumFarmer
 * never throws ClassNotFoundException, NoClassDefFoundError, or NoSuchMethodError
 * regardless of the SmartSpawner version or its absence.
 */
public class SmartSpawnerHook {

    private final HukumFarmer plugin;
    private boolean available = false;
    private String detectedVersion = "Unknown";

    // Common reflection targets across various SmartSpawner API releases
    private Object apiInstance = null;
    private Method getSpawnerAtMethod = null;
    private Method getStackAmountMethod = null;
    private Method getOwnerMethod = null;
    private Method getEntityTypeMethod = null;

    // Common PDC NamespacedKeys used by SmartSpawner variants
    private final NamespacedKey pdcStackKey;
    private final NamespacedKey pdcOwnerKey;
    private final NamespacedKey pdcTypeKey;
    private final NamespacedKey pdcDataKey;

    public SmartSpawnerHook(HukumFarmer plugin) {
        this.plugin = plugin;
        this.pdcStackKey = new NamespacedKey("smartspawner", "stack_amount");
        this.pdcOwnerKey = new NamespacedKey("smartspawner", "owner_uuid");
        this.pdcTypeKey = new NamespacedKey("smartspawner", "entity_type");
        this.pdcDataKey = new NamespacedKey("smartspawner", "spawner_data");
    }

    /**
     * Initializes and verifies SmartSpawner dependency with safe API reflection.
     *
     * @return true if SmartSpawner is active and API is bound, false otherwise.
     */
    public boolean init() {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("integrations.smartspawner.enabled", true)) {
            this.available = false;
            return false;
        }

        Plugin smartSpawnerPlugin = Bukkit.getPluginManager().getPlugin("SmartSpawner");
        if (smartSpawnerPlugin == null || !smartSpawnerPlugin.isEnabled()) {
            // Check alias/variant name
            smartSpawnerPlugin = Bukkit.getPluginManager().getPlugin("SmartSpawners");
        }

        if (smartSpawnerPlugin == null || !smartSpawnerPlugin.isEnabled()) {
            this.available = false;
            return false;
        }

        this.detectedVersion = smartSpawnerPlugin.getDescription().getVersion();

        try {
            // Attempt discovering the official SmartSpawner API class
            Class<?> apiClass = findSmartSpawnerApiClass();
            if (apiClass != null) {
                bindApiMethods(apiClass);
            }
            this.available = true;
            plugin.getLogger().info("Successfully hooked into SmartSpawner (v" + detectedVersion + ") integration!");
            return true;
        } catch (Throwable t) {
            this.available = true; // Still allow PDC and block-level compatibility
            plugin.getLogger().log(Level.WARNING, "SmartSpawner detected (v" + detectedVersion + "), using standard block/PDC compatibility layer: " + t.getMessage());
            return true;
        }
    }

    private Class<?> findSmartSpawnerApiClass() {
        String[] candidateClasses = new String[]{
                "net.smartplugins.smartspawner.api.SmartSpawnerAPI",
                "com.smartspawner.api.SmartSpawnerAPI",
                "me.smartspawner.api.SmartSpawnerAPI",
                "me.smartspawner.api.SmartSpawner",
                "dev.smartspawner.api.SmartSpawnerAPI",
                "dev.smartspawner.SmartSpawnerAPI"
        };

        for (String className : candidateClasses) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    private void bindApiMethods(Class<?> apiClass) {
        try {
            Method getApiMethod = null;
            try {
                getApiMethod = apiClass.getMethod("getInstance");
            } catch (NoSuchMethodException ignored) {
                try {
                    getApiMethod = apiClass.getMethod("getApi");
                } catch (NoSuchMethodException ignored2) {}
            }

            if (getApiMethod != null) {
                this.apiInstance = getApiMethod.invoke(null);
            }

            for (Method m : apiClass.getMethods()) {
                if (m.getName().equalsIgnoreCase("getSpawnerAt") || m.getName().equalsIgnoreCase("getSpawner")) {
                    if (m.getParameterCount() == 1 && (m.getParameterTypes()[0] == Location.class || m.getParameterTypes()[0] == Block.class)) {
                        this.getSpawnerAtMethod = m;
                    }
                }
            }
        } catch (Throwable t) {
            if (plugin.getConfigManager().getMainConfig().getBoolean("logging.debug", false)) {
                plugin.getLogger().warning("SmartSpawner API binding note: " + t.getMessage());
            }
        }
    }

    public boolean isAvailable() {
        return available && (Bukkit.getPluginManager().isPluginEnabled("SmartSpawner") || Bukkit.getPluginManager().isPluginEnabled("SmartSpawners"));
    }

    public String getDetectedVersion() {
        return detectedVersion;
    }

    /**
     * Checks whether a block is a Spawner managed by SmartSpawner or standard Minecraft Spawner.
     */
    public boolean isSmartSpawnerBlock(Block block) {
        if (block == null) return false;
        Material mat = block.getType();
        if (mat != Material.SPAWNER) return false;

        if (!isAvailable()) return false;

        // 1. Check API reflection
        if (getSpawnerAtMethod != null) {
            try {
                Object spawnerObj = getSpawnerAtMethod.invoke(apiInstance, block.getLocation());
                if (spawnerObj != null) return true;
            } catch (Throwable ignored) {}
        }

        // 2. Check PDC or CreatureSpawner state
        BlockState state = block.getState();
        if (state instanceof CreatureSpawner spawner) {
            PersistentDataContainer pdc = spawner.getPersistentDataContainer();
            if (pdc.has(pdcStackKey, PersistentDataType.INTEGER) ||
                pdc.has(pdcDataKey, PersistentDataType.STRING) ||
                pdc.has(pdcOwnerKey, PersistentDataType.STRING)) {
                return true;
            }
            return true; // Any valid spawner block when SmartSpawner is active
        }

        return false;
    }

    /**
     * Retrieves the stack count of a SmartSpawner at a given location.
     *
     * @param location Spawner location
     * @return Stack count (minimum 1)
     */
    public int getStackAmount(Location location) {
        if (!isAvailable() || location == null || location.getWorld() == null) return 1;

        // Try API
        if (getSpawnerAtMethod != null) {
            try {
                Object spawnerObj = getSpawnerAtMethod.invoke(apiInstance, location);
                if (spawnerObj != null) {
                    if (getStackAmountMethod == null) {
                        try {
                            getStackAmountMethod = spawnerObj.getClass().getMethod("getStackAmount");
                        } catch (NoSuchMethodException e) {
                            try {
                                getStackAmountMethod = spawnerObj.getClass().getMethod("getAmount");
                            } catch (NoSuchMethodException ignored) {}
                        }
                    }
                    if (getStackAmountMethod != null) {
                        Object amountObj = getStackAmountMethod.invoke(spawnerObj);
                        if (amountObj instanceof Number num) {
                            return Math.max(1, num.intValue());
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Try PDC
        Block block = location.getBlock();
        if (block.getType() == Material.SPAWNER) {
            BlockState state = block.getState();
            if (state instanceof CreatureSpawner spawner) {
                PersistentDataContainer pdc = spawner.getPersistentDataContainer();
                if (pdc.has(pdcStackKey, PersistentDataType.INTEGER)) {
                    Integer count = pdc.get(pdcStackKey, PersistentDataType.INTEGER);
                    if (count != null && count > 0) return count;
                }
            }
        }

        return 1;
    }

    /**
     * Retrieves the owner UUID of a SmartSpawner if recorded.
     */
    public Optional<UUID> getSpawnerOwner(Location location) {
        if (!isAvailable() || location == null || location.getWorld() == null) return Optional.empty();

        Block block = location.getBlock();
        if (block.getType() == Material.SPAWNER) {
            BlockState state = block.getState();
            if (state instanceof CreatureSpawner spawner) {
                PersistentDataContainer pdc = spawner.getPersistentDataContainer();
                if (pdc.has(pdcOwnerKey, PersistentDataType.STRING)) {
                    String uuidStr = pdc.get(pdcOwnerKey, PersistentDataType.STRING);
                    if (uuidStr != null && !uuidStr.isEmpty()) {
                        try {
                            return Optional.of(UUID.fromString(uuidStr));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Retrieves the spawned EntityType of the Spawner.
     */
    public EntityType getSpawnerEntityType(Location location) {
        if (location == null || location.getWorld() == null) return EntityType.UNKNOWN;

        Block block = location.getBlock();
        if (block.getType() == Material.SPAWNER) {
            BlockState state = block.getState();
            if (state instanceof CreatureSpawner spawner) {
                EntityType type = spawner.getSpawnedType();
                if (type != null) return type;
            }
        }

        return EntityType.UNKNOWN;
    }

    /**
     * Checks if an entity is marked by SmartSpawner (e.g. via PDC or metadata).
     */
    public boolean isSmartSpawnerEntity(Entity entity) {
        if (!isAvailable() || entity == null) return false;

        if (entity instanceof PersistentDataHolder holder) {
            PersistentDataContainer pdc = holder.getPersistentDataContainer();
            return pdc.has(pdcDataKey, PersistentDataType.STRING) ||
                   pdc.has(pdcStackKey, PersistentDataType.INTEGER);
        }

        return false;
    }
}
