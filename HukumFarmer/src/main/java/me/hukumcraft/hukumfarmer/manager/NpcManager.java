package me.hukumcraft.hukumfarmer.manager;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.message.MessageParser;
import me.hukumcraft.hukumfarmer.model.FarmerNPC;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Production-ready NPC and Hologram manager for Paper 1.21 - 1.21.11 and 26.x.
 * Uses MessageParser to render Adventure Components and TextDisplay holograms with full color support.
 */
public class NpcManager {

    private final HukumFarmer plugin;
    private final NamespacedKey npcKey;
    private final NamespacedKey holoKey;

    private final Map<String, FarmerNPC> npcs = new ConcurrentHashMap<>();

    public NpcManager(HukumFarmer plugin) {
        this.plugin = plugin;
        this.npcKey = new NamespacedKey(plugin, "hukumfarmer_npc_id");
        this.holoKey = new NamespacedKey(plugin, "hukumfarmer_holo_parent");
    }

    public void init() {
        loadAll();
    }

    public void loadAll() {
        cleanupRuntimeEntities();
        npcs.clear();

        FileConfiguration config = plugin.getConfigManager().getNpcConfig();
        ConfigurationSection section = config.getConfigurationSection("npcs");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection npcSec = section.getConfigurationSection(id);
            if (npcSec == null) continue;

            String worldName = npcSec.getString("world");
            if (worldName == null) {
                plugin.getLogger().warning("NPC '" + id + "' has no world configured! Skipping...");
                continue;
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World '" + worldName + "' for NPC '" + id + "' is not loaded! Skipping...");
                continue;
            }

            double x = npcSec.getDouble("x");
            double y = npcSec.getDouble("y");
            double z = npcSec.getDouble("z");
            float yaw = (float) npcSec.getDouble("yaw", 0.0);
            float pitch = (float) npcSec.getDouble("pitch", 0.0);
            Location loc = new Location(world, x, y, z, yaw, pitch);

            String name = npcSec.getString("display-name", config.getString("npc.display-name", "<gold><bold>HukumFarmer</bold></gold>"));
            List<String> holoLines = npcSec.getStringList("hologram");
            if (holoLines.isEmpty()) {
                holoLines = config.getStringList("npc.hologram.lines");
            }

            FarmerNPC npc = new FarmerNPC(id, name, loc, holoLines);
            npcs.put(id.toLowerCase(), npc);

            try {
                spawnNpc(npc);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to spawn NPC '" + id + "' at " + loc, e);
            }
        }

        plugin.getLogger().info("Successfully loaded and registered " + npcs.size() + " HukumFarmer NPCs.");
    }

    public FarmerNPC createNpc(String id, Location location, String displayName, List<String> holoLines) throws Exception {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location and world must not be null!");
        }

        String cleanId = (id != null && !id.trim().isEmpty()) ? id.trim().toLowerCase() : "npc_" + (npcs.size() + 1);

        if (displayName == null || displayName.isEmpty()) {
            displayName = plugin.getConfigManager().getNpcConfig().getString("npc.display-name", "<gold><bold>HukumFarmer</bold></gold>");
        }
        if (holoLines == null || holoLines.isEmpty()) {
            holoLines = plugin.getConfigManager().getNpcConfig().getStringList("npc.hologram.lines");
        }

        FarmerNPC npc = new FarmerNPC(cleanId, displayName, location, holoLines);
        npcs.put(cleanId, npc);

        // Spawn entity in world
        spawnNpc(npc);

        // Persist to configuration
        saveNpcToConfig(npc);

        return npc;
    }

    public void spawnNpc(FarmerNPC npc) {
        Location loc = npc.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        World world = loc.getWorld();

        // Ensure chunk is loaded before attempting spawn
        Chunk chunk = loc.getChunk();
        if (!chunk.isLoaded()) {
            chunk.load(true);
        }

        // Clean any existing entities associated with this NPC
        npc.removeEntities();

        FileConfiguration config = plugin.getConfigManager().getNpcConfig();
        String entityTypeName = config.getString("npc.entity-type", "VILLAGER").toUpperCase();

        Entity entity;
        if ("ARMOR_STAND".equals(entityTypeName)) {
            entity = world.spawn(loc, ArmorStand.class, stand -> {
                stand.customName(null);
                stand.setCustomNameVisible(false);
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setSilent(true);
                stand.setBasePlate(false);
                stand.setArms(true);
                stand.setPersistent(true);
                stand.setRemoveWhenFarAway(false);
                stand.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, npc.getId());
            });
        } else {
            // Default: Villager
            entity = world.spawn(loc, Villager.class, villager -> {
                villager.customName(null);
                villager.setCustomNameVisible(false);
                villager.setAI(false);
                villager.setAware(false);
                villager.setInvulnerable(true);
                villager.setSilent(true);
                villager.setCollidable(false);
                villager.setAdult();
                villager.setAgeLock(true);
                villager.setCanPickupItems(false);
                villager.setPersistent(true);
                villager.setRemoveWhenFarAway(false);

                String profName = config.getString("npc.villager-profession", "FARMER");
                try {
                    if ("FARMER".equalsIgnoreCase(profName)) {
                        villager.setProfession(Villager.Profession.FARMER);
                    } else if ("LIBRARIAN".equalsIgnoreCase(profName)) {
                        villager.setProfession(Villager.Profession.LIBRARIAN);
                    } else if ("CLERIC".equalsIgnoreCase(profName)) {
                        villager.setProfession(Villager.Profession.CLERIC);
                    } else if ("BLACKSMITH".equalsIgnoreCase(profName) || "WEAPONSMITH".equalsIgnoreCase(profName)) {
                        villager.setProfession(Villager.Profession.WEAPONSMITH);
                    } else if ("BUTCHER".equalsIgnoreCase(profName)) {
                        villager.setProfession(Villager.Profession.BUTCHER);
                    } else if ("NITWIT".equalsIgnoreCase(profName)) {
                        villager.setProfession(Villager.Profession.NITWIT);
                    } else {
                        villager.setProfession(Villager.Profession.FARMER);
                    }
                } catch (Throwable ignored) {
                    try {
                        villager.setProfession(Villager.Profession.FARMER);
                    } catch (Throwable ignored2) {}
                }

                villager.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, npc.getId());
            });
        }

        npc.setNpcEntity(entity);

        // Spawn Holograms
        if (config.getBoolean("npc.hologram.enabled", true) && !npc.getHologramLines().isEmpty()) {
            double heightOffset = config.getDouble("npc.hologram.height-offset", 2.1);
            List<String> lines = npc.getHologramLines();
            double lineSpacing = 0.28;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                double yOffset = heightOffset + ((lines.size() - 1 - i) * lineSpacing);
                Location holoLoc = loc.clone().add(0, yOffset, 0);

                try {
                    TextDisplay textDisplay = world.spawn(holoLoc, TextDisplay.class, td -> {
                        td.text(MessageParser.parse(line));
                        td.setBillboard(Display.Billboard.CENTER);
                        td.setShadowed(true);
                        td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                        td.setPersistent(true);
                        td.getPersistentDataContainer().set(holoKey, PersistentDataType.STRING, npc.getId());
                    });
                    npc.addHologramEntity(textDisplay);
                } catch (Throwable e) {
                    ArmorStand holoStand = world.spawn(holoLoc, ArmorStand.class, stand -> {
                        stand.customName(MessageParser.parse(line));
                        stand.setCustomNameVisible(true);
                        stand.setInvisible(true);
                        stand.setMarker(true);
                        stand.setGravity(false);
                        stand.setInvulnerable(true);
                        stand.setSilent(true);
                        stand.setPersistent(true);
                        stand.getPersistentDataContainer().set(holoKey, PersistentDataType.STRING, npc.getId());
                    });
                    npc.addHologramEntity(holoStand);
                }
            }
        }
    }

    public boolean removeNpc(String id) {
        if (id == null) return false;
        FarmerNPC npc = npcs.remove(id.toLowerCase());
        if (npc != null) {
            npc.removeEntities();
            removeNpcFromConfig(id.toLowerCase());

            Location loc = npc.getLocation();
            if (loc != null && loc.getWorld() != null) {
                for (Entity entity : loc.getWorld().getEntities()) {
                    if (id.equalsIgnoreCase(entity.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING)) ||
                            id.equalsIgnoreCase(entity.getPersistentDataContainer().get(holoKey, PersistentDataType.STRING))) {
                        entity.remove();
                    }
                }
            }
            return true;
        }
        return false;
    }

    public Optional<FarmerNPC> getNearestNpc(Location location, double maxDistance) {
        if (location == null || location.getWorld() == null) return Optional.empty();
        FarmerNPC nearest = null;
        double nearestDistSq = maxDistance * maxDistance;

        for (FarmerNPC npc : npcs.values()) {
            if (npc.getLocation() != null && npc.getLocation().getWorld() != null
                    && npc.getLocation().getWorld().equals(location.getWorld())) {
                double distSq = npc.getLocation().distanceSquared(location);
                if (distSq <= nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = npc;
                }
            }
        }

        return Optional.ofNullable(nearest);
    }

    public Optional<FarmerNPC> getNpc(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(npcs.get(id.toLowerCase()));
    }

    public Collection<FarmerNPC> getAllNpcs() {
        return Collections.unmodifiableCollection(npcs.values());
    }

    public boolean isFarmerNpc(Entity entity) {
        if (entity == null) return false;
        return entity.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING);
    }

    public boolean isHologram(Entity entity) {
        if (entity == null) return false;
        return entity.getPersistentDataContainer().has(holoKey, PersistentDataType.STRING);
    }

    public String getNpcId(Entity entity) {
        if (entity == null) return null;
        String id = entity.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
        if (id == null) {
            id = entity.getPersistentDataContainer().get(holoKey, PersistentDataType.STRING);
        }
        return id;
    }

    public void checkChunkNpcs(Chunk chunk) {
        if (chunk == null) return;
        for (FarmerNPC npc : npcs.values()) {
            Location loc = npc.getLocation();
            if (loc != null && loc.getWorld() != null && loc.getWorld().equals(chunk.getWorld())) {
                if ((loc.getBlockX() >> 4) == chunk.getX() && (loc.getBlockZ() >> 4) == chunk.getZ()) {
                    if (npc.getNpcEntity() == null || !npc.getNpcEntity().isValid()) {
                        spawnNpc(npc);
                    }
                }
            }
        }
    }

    public void cleanupRuntimeEntities() {
        for (FarmerNPC npc : npcs.values()) {
            npc.removeEntities();
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING) ||
                        entity.getPersistentDataContainer().has(holoKey, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
    }

    private void saveNpcToConfig(FarmerNPC npc) {
        FileConfiguration config = plugin.getConfigManager().getNpcConfig();
        String path = "npcs." + npc.getId();
        Location loc = npc.getLocation();

        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", (double) loc.getYaw());
        config.set(path + ".pitch", (double) loc.getPitch());
        config.set(path + ".display-name", npc.getName());
        config.set(path + ".hologram", npc.getHologramLines());

        plugin.getConfigManager().saveNpcConfig();
    }

    private void removeNpcFromConfig(String id) {
        FileConfiguration config = plugin.getConfigManager().getNpcConfig();
        config.set("npcs." + id, null);
        plugin.getConfigManager().saveNpcConfig();
    }
}
