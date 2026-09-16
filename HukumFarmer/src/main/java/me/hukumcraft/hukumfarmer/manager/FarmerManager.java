package me.hukumcraft.hukumfarmer.manager;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.database.FarmerRepository;
import me.hukumcraft.hukumfarmer.message.MessageParser;
import me.hukumcraft.hukumfarmer.model.Farmer;
import me.hukumcraft.hukumfarmer.model.FarmerNPC;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * In-memory manager and cache for active farmers with 1-to-1 NPC bindings,
 * atomic server-side transaction locks, and startup orphan NPC reconciliation.
 */
public class FarmerManager {

    private final HukumFarmer plugin;
    private final FarmerRepository repository;

    private final Map<UUID, Farmer> farmersByOwner = new ConcurrentHashMap<>();
    private final Map<String, UUID> farmerLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> renamePrompts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> activeTransactions = new ConcurrentHashMap<>();

    // Lock expiry: 15 seconds to safely prevent permanent deadlocks
    private static final long TRANSACTION_LOCK_TIMEOUT_MS = 15000L;

    public FarmerManager(HukumFarmer plugin, FarmerRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public void loadAll() {
        farmersByOwner.clear();
        farmerLocations.clear();
        repository.loadAllFarmersAsync().thenAccept(loadedFarmers -> {
            for (Farmer farmer : loadedFarmers) {
                farmersByOwner.put(farmer.getOwnerUUID(), farmer);
                indexLocation(farmer);

                // Ensure NPC is spawned for each farmer if location is valid
                if (farmer.getLocation() != null && farmer.getLocation().getWorld() != null) {
                    try {
                        List<String> holoLines = buildHologramLines(farmer);
                        if (plugin.getNpcManager().getNpc(farmer.getNpcId()).isEmpty()) {
                            plugin.getNpcManager().createNpc(farmer.getNpcId(), farmer.getLocation(), farmer.getCustomName(), holoLines);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to restore NPC for farmer: " + farmer.getOwnerName(), e);
                    }
                }
            }
            plugin.getLogger().info("Successfully loaded " + loadedFarmers.size() + " farmers into memory.");

            // Perform Orphan NPC audit
            auditNpcReconciliation();
        });
    }

    /**
     * Reconciles registered NPCs with existing farmer records.
     */
    public void auditNpcReconciliation() {
        for (Farmer farmer : farmersByOwner.values()) {
            if (farmer.getNpcId() != null) {
                Optional<FarmerNPC> optNpc = plugin.getNpcManager().getNpc(farmer.getNpcId());
                if (optNpc.isEmpty() && farmer.getLocation() != null && farmer.getLocation().getWorld() != null) {
                    plugin.getLogger().info("Restoring missing NPC for farmer owner: " + farmer.getOwnerName() + " (ID: " + farmer.getNpcId() + ")");
                    try {
                        plugin.getNpcManager().createNpc(farmer.getNpcId(), farmer.getLocation(), farmer.getCustomName(), buildHologramLines(farmer));
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Could not recreate missing NPC for " + farmer.getOwnerName(), e);
                    }
                }
            }
        }
    }

    public Optional<Farmer> getFarmer(UUID ownerUUID) {
        if (ownerUUID == null) return Optional.empty();
        return Optional.ofNullable(farmersByOwner.get(ownerUUID));
    }

    public Optional<Farmer> getFarmerByNpcId(String npcId) {
        if (npcId == null) return Optional.empty();
        for (Farmer farmer : farmersByOwner.values()) {
            if (npcId.equalsIgnoreCase(farmer.getNpcId())) {
                return Optional.of(farmer);
            }
        }
        return Optional.empty();
    }

    public Optional<Farmer> getFarmerByLocation(Location location) {
        if (location == null || location.getWorld() == null) return Optional.empty();
        String locKey = toLocationKey(location);
        UUID ownerUUID = farmerLocations.get(locKey);
        if (ownerUUID == null) return Optional.empty();
        return getFarmer(ownerUUID);
    }

    /**
     * Acquires server-side transaction lock for the player.
     * Prevents double clicking, spamming, and concurrent purchase race conditions.
     */
    public boolean acquireTransactionLock(UUID uuid) {
        if (uuid == null) return false;
        long now = System.currentTimeMillis();
        Long activeTime = activeTransactions.get(uuid);
        if (activeTime != null && (now - activeTime) < TRANSACTION_LOCK_TIMEOUT_MS) {
            return false;
        }
        activeTransactions.put(uuid, now);
        return true;
    }

    public void releaseTransactionLock(UUID uuid) {
        if (uuid != null) {
            activeTransactions.remove(uuid);
        }
    }

    /**
     * Executes an atomic farmer egg purchase transaction with inventory validation and rollback protection.
     */
    public boolean processFarmerPurchase(Player player) {
        UUID uuid = player.getUniqueId();
        if (!acquireTransactionLock(uuid)) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-purchase-in-progress"));
            return false;
        }

        try {
            // 1. Validate Config & Enabled Status
            if (!plugin.getConfigManager().getMainConfig().getBoolean("farmer.purchase.enabled", true)) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-purchase-disabled"));
                return false;
            }

            // 2. Validate Limit
            int maxLimit = plugin.getConfigManager().getMainConfig().getInt("farmer.max-per-player", 1);
            if (getFarmer(uuid).isPresent() && maxLimit <= 1) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-limit-reached", "%max%", String.valueOf(maxLimit)));
                return false;
            }

            // 3. Prepare Farmer Egg Item & Check Inventory Space BEFORE charging
            org.bukkit.inventory.ItemStack egg = plugin.getFarmerItemManager().createFarmerEgg(1);
            if (!plugin.getFarmerItemManager().hasInventorySpace(player, egg)) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player, "inventory-full"));
                return false;
            }

            // 4. Validate Economy & Price
            double price = plugin.getConfigManager().getMainConfig().getDouble("farmer.purchase.price", 50000.0);
            if (price < 0.0) price = 0.0;

            if (price > 0.0) {
                if (!plugin.getVaultHook().isHooked()) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player, "vault-disabled-warning"));
                    return false;
                }

                double balance = plugin.getVaultHook().getBalance(player);
                if (balance < price) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-purchase-failed-money",
                            "%price%", MessageParser.formatMoney(price),
                            "%balance%", MessageParser.formatMoney(balance)));
                    return false;
                }

                // Withdraw money with transaction safety
                if (!plugin.getVaultHook().withdraw(player, price)) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-purchase-error"));
                    return false;
                }
            }

            // 5. Safely Deliver Farmer Egg to Inventory with rollback guarantee
            try {
                java.util.Map<Integer, org.bukkit.inventory.ItemStack> overflow = player.getInventory().addItem(egg);
                if (!overflow.isEmpty()) {
                    for (org.bukkit.inventory.ItemStack dropped : overflow.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), dropped);
                    }
                }
                plugin.getSecurityManager().logSecurityEvent("FARMER_EGG_PURCHASED", player.getName(), "Purchased Farmer Egg for " + price + "$");
            } catch (Exception e) {
                // Refund money if item delivery failed
                if (price > 0.0 && plugin.getVaultHook().isHooked()) {
                    plugin.getVaultHook().deposit(player, price);
                }
                plugin.getLogger().log(Level.SEVERE, "Unexpected error during farmer egg delivery for " + player.getName() + ", refunded " + price + "$", e);
                player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-purchase-error"));
                return false;
            }

            // 6. Success Notification
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "purchase-success-egg", "%price%", MessageParser.formatMoney(price)));
            return true;
        } finally {
            releaseTransactionLock(uuid);
        }
    }

    public Farmer createFarmer(Player player, Location location) {
        String defaultName = plugin.getConfigManager().getMainConfig().getString("farmer.default-name", "%player%'s Farmer");
        Farmer farmer = Farmer.createNew(player.getUniqueId(), player.getName(), location, defaultName);
        farmersByOwner.put(player.getUniqueId(), farmer);
        indexLocation(farmer);

        // Automatically create and spawn the farmer's dedicated NPC
        try {
            List<String> holoLines = buildHologramLines(farmer);
            plugin.getNpcManager().createNpc(farmer.getNpcId(), location, farmer.getCustomName(), holoLines);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to automatically spawn NPC for farmer: " + player.getName(), e);
        }

        repository.saveFarmerAsync(farmer);
        return farmer;
    }

    public void deleteFarmer(UUID ownerUUID) {
        Farmer farmer = farmersByOwner.remove(ownerUUID);
        if (farmer != null) {
            if (farmer.getLocation() != null) {
                farmerLocations.remove(toLocationKey(farmer.getLocation()));
            }
            // Automatically remove the associated NPC
            if (farmer.getNpcId() != null) {
                plugin.getNpcManager().removeNpc(farmer.getNpcId());
            }
        }
        repository.deleteFarmerAsync(ownerUUID);
    }

    public void updateFarmerLocation(Farmer farmer, Location newLoc) {
        if (farmer.getLocation() != null) {
            farmerLocations.remove(toLocationKey(farmer.getLocation()));
        }
        farmer.setLocation(newLoc);
        indexLocation(farmer);

        // Move / respawn the associated NPC
        if (farmer.getNpcId() != null) {
            plugin.getNpcManager().getNpc(farmer.getNpcId()).ifPresent(npc -> {
                npc.setLocation(newLoc);
                plugin.getNpcManager().spawnNpc(npc);
            });
        }

        farmer.markDirty();
    }

    private List<String> buildHologramLines(Farmer farmer) {
        List<String> template = plugin.getConfigManager().getNpcConfig().getStringList("npc.hologram.lines");
        if (template.isEmpty()) {
            template = Arrays.asList(
                    "<gold><bold>%farmer_name%</bold></gold>",
                    "<yellow>Sahip: <gold>%owner%</gold></yellow>",
                    "<gray>[Sağ Tık] Çiftçi Menüsünü Aç</gray>"
            );
        }

        List<String> resolved = new ArrayList<>();
        for (String line : template) {
            resolved.add(line.replace("%farmer_name%", MessageParser.escapeMiniMessage(farmer.getCustomName()))
                    .replace("%owner%", MessageParser.escapeMiniMessage(farmer.getOwnerName()))
                    .replace("%level%", String.valueOf(farmer.getLevel())));
        }
        return resolved;
    }

    public void updateFarmerHologram(Farmer farmer) {
        if (farmer == null || farmer.getNpcId() == null) return;
        plugin.getNpcManager().getNpc(farmer.getNpcId()).ifPresent(npc -> {
            npc.setHologramLines(buildHologramLines(farmer));
            plugin.getNpcManager().spawnNpc(npc);
        });
    }

    private void indexLocation(Farmer farmer) {
        if (farmer.getLocation() != null && farmer.getLocation().getWorld() != null) {
            farmerLocations.put(toLocationKey(farmer.getLocation()), farmer.getOwnerUUID());
        }
    }

    private String toLocationKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public Collection<Farmer> getActiveFarmers() {
        return Collections.unmodifiableCollection(farmersByOwner.values());
    }

    public List<Farmer> getDirtyFarmers() {
        return farmersByOwner.values().stream().filter(Farmer::isDirty).collect(Collectors.toList());
    }

    public void saveDirtyFarmersAsync() {
        List<Farmer> dirty = getDirtyFarmers();
        if (!dirty.isEmpty()) {
            repository.saveFarmersBatchAsync(dirty);
            if (plugin.getConfigManager().getMainConfig().getBoolean("logging.debug", false)) {
                plugin.getLogger().info("Saved " + dirty.size() + " modified farmers to database.");
            }
        }
    }

    public void saveAllSync() {
        Collection<Farmer> all = farmersByOwner.values();
        if (!all.isEmpty()) {
            repository.saveFarmersBatchSync(all);
            plugin.getLogger().info("Saved all " + all.size() + " farmers to database synchronously.");
        }
    }

    public void startRenamePrompt(UUID playerUUID) {
        renamePrompts.put(playerUUID, System.currentTimeMillis() + 60000);
    }

    public boolean isRenaming(UUID playerUUID) {
        Long timeout = renamePrompts.get(playerUUID);
        if (timeout == null) return false;
        if (System.currentTimeMillis() > timeout) {
            renamePrompts.remove(playerUUID);
            return false;
        }
        return true;
    }

    public void cancelRenamePrompt(UUID playerUUID) {
        renamePrompts.remove(playerUUID);
    }
}
