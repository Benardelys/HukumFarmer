package me.hukumcraft.hukumfarmer.gui;

import me.hukumcraft.hukumfarmer.model.Farmer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom InventoryHolder to cleanly associate GUI events with Farmer instances and views.
 */
public class FarmerGuiHolder implements InventoryHolder {

    private final Farmer farmer;
    private final GuiType guiType;
    private Inventory inventory;
    private final Map<Integer, String> actionSlots = new HashMap<>();

    public FarmerGuiHolder(Farmer farmer, GuiType guiType) {
        this.farmer = farmer;
        this.guiType = guiType;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Farmer getFarmer() {
        return farmer;
    }

    public GuiType getGuiType() {
        return guiType;
    }

    public void setAction(int slot, String actionKey) {
        actionSlots.put(slot, actionKey);
    }

    public String getAction(int slot) {
        return actionSlots.get(slot);
    }
}
