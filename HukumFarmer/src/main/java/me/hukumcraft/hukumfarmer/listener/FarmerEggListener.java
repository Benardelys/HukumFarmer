package me.hukumcraft.hukumfarmer.listener;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.model.Farmer;
import me.hukumcraft.hukumfarmer.util.SoundEffectUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Listens for Farmer Egg interactions:
 * - Handles physical placement of the Farmer in the world and instant bound NPC spawning.
 * - Prevents creative mode item duplication.
 * - Enforces WorldGuard region protection.
 * - Prevents crafting and anvil exploit modifications.
 */
public class FarmerEggListener implements Listener {

    private final HukumFarmer plugin;

    public FarmerEggListener(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only process main-hand interactions
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !plugin.getFarmerItemManager().isFarmerEgg(item)) {
            return;
        }

        // Always cancel vanilla block placement / item consumption
        event.setCancelled(true);

        Player player = event.getPlayer();

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        // 1. Check Permission
        if (!player.hasPermission("hukumfarmer.use") && !player.hasPermission("hukumfarmer.create")) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
            return;
        }

        // 2. Check Farmer Limit
        UUID uuid = player.getUniqueId();
        int maxLimit = plugin.getConfigManager().getMainConfig().getInt("farmer.max-per-player", 1);
        if (plugin.getFarmerManager().getFarmer(uuid).isPresent() && maxLimit <= 1) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-limit-reached", "%max%", String.valueOf(maxLimit)));
            return;
        }

        // 3. Compute Target Location
        Block targetBlock = clickedBlock.getRelative(event.getBlockFace());
        if (targetBlock.getType() == Material.LAVA) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-invalid-location"));
            return;
        }

        Location placeLoc = targetBlock.getLocation().add(0.5, 0, 0.5);
        placeLoc.setYaw(player.getLocation().getYaw() + 180f);
        placeLoc.setPitch(0f);

        // 4. WorldGuard Region Check
        if (!plugin.getWorldGuardHook().canBuild(player, placeLoc)) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-item-cannot-place-region"));
            SoundEffectUtil.playEffect(player, plugin.getConfigManager().getMainConfig().getConfigurationSection("effects.error"));
            return;
        }

        // 5. Atomic Lock & Farmer Creation
        if (!plugin.getFarmerManager().acquireTransactionLock(uuid)) {
            return;
        }

        try {
            // Re-verify limit inside transaction lock
            if (plugin.getFarmerManager().getFarmer(uuid).isPresent() && maxLimit <= 1) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-limit-reached", "%max%", String.valueOf(maxLimit)));
                return;
            }

            Farmer farmer = plugin.getFarmerManager().createFarmer(player, placeLoc);
            if (farmer == null) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-creation-failed"));
                return;
            }

            // Deduct exactly 1 Farmer Egg from main hand
            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (plugin.getFarmerItemManager().isFarmerEgg(inHand)) {
                if (inHand.getAmount() > 1) {
                    inHand.setAmount(inHand.getAmount() - 1);
                    player.getInventory().setItemInMainHand(inHand);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
            }

            // Play creation visual & sound effects
            SoundEffectUtil.playEffect(player, plugin.getConfigManager().getMainConfig().getConfigurationSection("effects.farmer-created"));
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-placed"));

            // Open main GUI for the newly placed farmer
            plugin.getMainMenuGui().open(player, farmer);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create farmer from egg for " + player.getName(), e);
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-creation-failed"));
        } finally {
            plugin.getFarmerManager().releaseTransactionLock(uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreativeInventory(InventoryCreativeEvent event) {
        if (!plugin.getFarmerItemManager().isPreventCreativeDuplication()) {
            return;
        }
        if (plugin.getFarmerItemManager().isFarmerEgg(event.getCursor()) || plugin.getFarmerItemManager().isFarmerEgg(event.getCurrentItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (plugin.getFarmerItemManager().isFarmerEgg(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (plugin.getFarmerItemManager().isFarmerEgg(item)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getFirstItem();
        ItemStack second = event.getInventory().getSecondItem();
        if (plugin.getFarmerItemManager().isFarmerEgg(first) || plugin.getFarmerItemManager().isFarmerEgg(second)) {
            event.setResult(null);
        }
    }
}
