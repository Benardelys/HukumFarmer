package me.hukumcraft.hukumfarmer.hook.axminions;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.model.CropConfig;
import me.hukumcraft.hukumfarmer.model.Farmer;
import me.hukumcraft.hukumfarmer.model.FarmerLevel;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles interactions and crop forwarding between AxMinions and HukumFarmer.
 */
public class AxMinionsManager implements Listener {

    private final HukumFarmer plugin;
    private final AxMinionsHook hook;

    public AxMinionsManager(HukumFarmer plugin, AxMinionsHook hook) {
        this.plugin = plugin;
        this.hook = hook;
    }

    public void init() {
        if (!hook.isHooked()) return;
        // Listener registration is performed safely if AxMinions is active
    }

    /**
     * Forwards a crop harvested by an AxMinion belonging to a player into their HukumFarmer storage.
     *
     * @param ownerUUID The UUID of the minion's owner
     * @param cropMaterial The harvested crop Material
     * @param amount The base harvested amount
     * @return true if the crop was deposited into farmer storage, false otherwise
     */
    public boolean forwardMinionHarvest(UUID ownerUUID, Material cropMaterial, long amount) {
        if (!hook.isHooked() || ownerUUID == null || cropMaterial == null || amount <= 0) {
            return false;
        }

        if (!plugin.getConfigManager().getMainConfig().getBoolean("integrations.axminions.affect-farmer-storage", true)) {
            return false;
        }

        Optional<Farmer> optFarmer = plugin.getFarmerManager().getFarmer(ownerUUID);
        if (optFarmer.isEmpty()) {
            return false;
        }

        Optional<CropConfig> optCrop = plugin.getCropManager().getCropByItem(cropMaterial);
        if (optCrop.isEmpty() || !optCrop.get().isEnabled()) {
            return false;
        }

        Farmer farmer = optFarmer.get();
        FarmerLevel level = plugin.getLevelManager().getLevel(farmer.getLevel());

        long finalAmount = amount;
        if (plugin.getConfigManager().getMainConfig().getBoolean("integrations.axminions.apply-level-multiplier", true)) {
            finalAmount = Math.max(1, Math.round(amount * level.getMultiplier()));
        }

        long added = farmer.addCrop(optCrop.get().getId(), finalAmount, level.getMaxStorage());
        if (added > 0) {
            farmer.addXp(optCrop.get().getXpReward() * added);
            farmer.markDirty();
            return true;
        }

        return false;
    }
}
