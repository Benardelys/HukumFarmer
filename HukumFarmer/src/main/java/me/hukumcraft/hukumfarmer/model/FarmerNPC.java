package me.hukumcraft.hukumfarmer.model;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Model representing an in-game HukumFarmer merchant NPC.
 */
public class FarmerNPC {

    private final String id;
    private String name;
    private Location location;
    private List<String> hologramLines;

    private Entity npcEntity;
    private final List<Entity> hologramEntities = new ArrayList<>();

    public FarmerNPC(String id, String name, Location location, List<String> hologramLines) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.hologramLines = hologramLines != null ? new ArrayList<>(hologramLines) : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public List<String> getHologramLines() {
        return hologramLines;
    }

    public void setHologramLines(List<String> hologramLines) {
        this.hologramLines = hologramLines != null ? new ArrayList<>(hologramLines) : new ArrayList<>();
    }

    public Entity getNpcEntity() {
        return npcEntity;
    }

    public void setNpcEntity(Entity npcEntity) {
        this.npcEntity = npcEntity;
    }

    public List<Entity> getHologramEntities() {
        return hologramEntities;
    }

    public void addHologramEntity(Entity entity) {
        if (entity != null) {
            hologramEntities.add(entity);
        }
    }

    public void clearHologramEntities() {
        for (Entity e : hologramEntities) {
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
        hologramEntities.clear();
    }

    public void removeEntities() {
        if (npcEntity != null && npcEntity.isValid()) {
            npcEntity.remove();
            npcEntity = null;
        }
        clearHologramEntities();
    }
}
