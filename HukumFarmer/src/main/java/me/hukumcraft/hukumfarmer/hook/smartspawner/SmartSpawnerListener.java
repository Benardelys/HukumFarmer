package me.hukumcraft.hukumfarmer.hook.smartspawner;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.List;

/**
 * Event listener for SmartSpawner entity deaths and spawner-related drop processing.
 * Safely collects mob drops into nearby active farmer storage when enabled.
 */
public class SmartSpawnerListener implements Listener {

    private final HukumFarmer plugin;
    private final SmartSpawnerManager manager;

    public SmartSpawnerListener(HukumFarmer plugin, SmartSpawnerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!manager.isIntegrationActive()) return;

        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return; // Never intercept player deaths

        Location loc = entity.getLocation();
        List<ItemStack> drops = event.getDrops();
        if (drops.isEmpty()) return;

        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            if (drop != null && manager.isAllowedDrop(drop.getType())) {
                boolean collected = manager.processSpawnerMobDrop(loc, drop);
                if (collected) {
                    iterator.remove(); // Prevent drop on ground since farmer collected it
                }
            }
        }
    }
}
