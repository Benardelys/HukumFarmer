package me.hukumcraft.hukumfarmer.listener;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.gui.MainMenuGui;
import me.hukumcraft.hukumfarmer.model.Farmer;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

/**
 * Handles block interactions at farmer anchor coordinates.
 */
public class FarmerBlockListener implements Listener {

    private final HukumFarmer plugin;
    private final MainMenuGui mainMenuGui;

    public FarmerBlockListener(HukumFarmer plugin, MainMenuGui mainMenuGui) {
        this.plugin = plugin;
        this.mainMenuGui = mainMenuGui;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        Location loc = clickedBlock.getLocation();
        Optional<Farmer> optFarmer = plugin.getFarmerManager().getFarmerByLocation(loc);
        if (optFarmer.isEmpty()) return;

        Farmer farmer = optFarmer.get();
        Player player = event.getPlayer();

        if (farmer.getOwnerUUID().equals(player.getUniqueId()) || player.hasPermission("hukumfarmer.admin")) {
            event.setCancelled(true);
            mainMenuGui.open(player, farmer);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        Optional<Farmer> optFarmer = plugin.getFarmerManager().getFarmerByLocation(loc);
        if (optFarmer.isEmpty()) return;

        Player player = event.getPlayer();
        Farmer farmer = optFarmer.get();

        if (!farmer.getOwnerUUID().equals(player.getUniqueId()) && !player.hasPermission("hukumfarmer.admin")) {
            event.setCancelled(true);
        }
    }
}
