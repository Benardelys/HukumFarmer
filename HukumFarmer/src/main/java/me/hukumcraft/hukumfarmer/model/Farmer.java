package me.hukumcraft.hukumfarmer.model;

import org.bukkit.Location;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe domain model representing a player's Farmer instance.
 */
public class Farmer {

    private final UUID id;
    private final UUID ownerUUID;
    private String ownerName;
    private String customName;
    private Location location;
    private String npcId;

    private int level;
    private long xp;
    private double earnings;

    private boolean autoHarvest;
    private boolean autoSell;

    private final ConcurrentHashMap<String, Long> storedCrops = new ConcurrentHashMap<>();

    private long totalHarvested;
    private double totalEarnings;
    private final long createdAt;
    private long lastHarvestTime;

    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public Farmer(UUID id, UUID ownerUUID, String ownerName, String customName,
                  Location location, String npcId, int level, long xp, double earnings,
                  boolean autoHarvest, boolean autoSell,
                  long totalHarvested, double totalEarnings,
                  long createdAt, long lastHarvestTime) {
        this.id = id != null ? id : UUID.randomUUID();
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.customName = (customName != null && !customName.isEmpty()) ? customName : ownerName + "'s Farmer";
        this.location = location;
        this.npcId = (npcId != null && !npcId.isEmpty()) ? npcId : "farmer-" + ownerUUID.toString();
        this.level = Math.max(1, level);
        this.xp = Math.max(0, xp);
        this.earnings = Math.max(0, earnings);
        this.autoHarvest = autoHarvest;
        this.autoSell = autoSell;
        this.totalHarvested = Math.max(0, totalHarvested);
        this.totalEarnings = Math.max(0, totalEarnings);
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
        this.lastHarvestTime = Math.max(0, lastHarvestTime);
    }

    public static Farmer createNew(UUID ownerUUID, String ownerName, Location location, String defaultName) {
        String name = (defaultName != null && !defaultName.isEmpty()) ? defaultName.replace("%player%", ownerName) : ownerName + "'s Farmer";
        String npcId = "farmer-" + ownerUUID.toString();
        Farmer farmer = new Farmer(UUID.randomUUID(), ownerUUID, ownerName, name, location, npcId, 1, 0, 0.0, true, false, 0, 0.0, System.currentTimeMillis(), 0);
        farmer.markDirty();
        return farmer;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
        markDirty();
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
        markDirty();
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
        markDirty();
    }

    public String getNpcId() {
        return npcId;
    }

    public void setNpcId(String npcId) {
        this.npcId = npcId;
        markDirty();
    }

    public synchronized int getLevel() {
        return level;
    }

    public synchronized void setLevel(int level) {
        this.level = Math.max(1, level);
        markDirty();
    }

    public synchronized long getXp() {
        return xp;
    }

    public synchronized void setXp(long xp) {
        this.xp = Math.max(0, xp);
        markDirty();
    }

    public synchronized void addXp(long amount) {
        if (amount <= 0) return;
        this.xp += amount;
        markDirty();
    }

    public synchronized double getEarnings() {
        return earnings;
    }

    public synchronized void setEarnings(double earnings) {
        this.earnings = Math.max(0, earnings);
        markDirty();
    }

    public synchronized void addEarnings(double amount) {
        if (amount <= 0) return;
        this.earnings += amount;
        this.totalEarnings += amount;
        markDirty();
    }

    public synchronized double collectEarnings() {
        double collected = this.earnings;
        this.earnings = 0;
        markDirty();
        return collected;
    }

    public synchronized boolean isAutoHarvest() {
        return autoHarvest;
    }

    public synchronized void setAutoHarvest(boolean autoHarvest) {
        this.autoHarvest = autoHarvest;
        markDirty();
    }

    public synchronized boolean isAutoSell() {
        return autoSell;
    }

    public synchronized void setAutoSell(boolean autoSell) {
        this.autoSell = autoSell;
        markDirty();
    }

    public Map<String, Long> getStoredCrops() {
        return Collections.unmodifiableMap(storedCrops);
    }

    public long getCropAmount(String cropId) {
        if (cropId == null) return 0;
        return storedCrops.getOrDefault(cropId.toUpperCase(), 0L);
    }

    public synchronized void setCropAmount(String cropId, long amount) {
        if (cropId == null) return;
        if (amount <= 0) {
            storedCrops.remove(cropId.toUpperCase());
        } else {
            storedCrops.put(cropId.toUpperCase(), amount);
        }
        markDirty();
    }

    public synchronized long addCrop(String cropId, long amount, long maxStorage) {
        if (cropId == null || amount <= 0) return 0;
        long currentTotal = getTotalStoredItems();
        long spaceLeft = Math.max(0, maxStorage - currentTotal);
        long toAdd = Math.min(amount, spaceLeft);

        if (toAdd > 0) {
            storedCrops.merge(cropId.toUpperCase(), toAdd, Long::sum);
            this.totalHarvested += toAdd;
            markDirty();
        }
        return toAdd;
    }

    public synchronized long removeCrop(String cropId, long amount) {
        if (cropId == null || amount <= 0) return 0;
        String key = cropId.toUpperCase();
        long current = storedCrops.getOrDefault(key, 0L);
        if (current <= 0) return 0;

        long toRemove = Math.min(amount, current);
        long remaining = current - toRemove;
        if (remaining <= 0) {
            storedCrops.remove(key);
        } else {
            storedCrops.put(key, remaining);
        }
        markDirty();
        return toRemove;
    }

    public synchronized void clearStoredCrops() {
        storedCrops.clear();
        markDirty();
    }

    public long getTotalStoredItems() {
        long total = 0;
        for (long count : storedCrops.values()) {
            total += count;
        }
        return total;
    }

    public synchronized long getTotalHarvested() {
        return totalHarvested;
    }

    public synchronized void setTotalHarvested(long totalHarvested) {
        this.totalHarvested = totalHarvested;
        markDirty();
    }

    public synchronized double getTotalEarnings() {
        return totalEarnings;
    }

    public synchronized void setTotalEarnings(double totalEarnings) {
        this.totalEarnings = totalEarnings;
        markDirty();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public synchronized long getLastHarvestTime() {
        return lastHarvestTime;
    }

    public synchronized void setLastHarvestTime(long lastHarvestTime) {
        this.lastHarvestTime = lastHarvestTime;
        markDirty();
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public void markDirty() {
        dirty.set(true);
    }

    public void resetDirty() {
        dirty.set(false);
    }
}
