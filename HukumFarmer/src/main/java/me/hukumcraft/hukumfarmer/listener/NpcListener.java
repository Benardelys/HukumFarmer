package me.hukumcraft.hukumfarmer.listener;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.gui.MainMenuGui;
import me.hukumcraft.hukumfarmer.gui.PurchaseGui;
import me.hukumcraft.hukumfarmer.model.Farmer;
import me.hukumcraft.hukumfarmer.util.SoundEffectUtil;
import me.hukumcraft.hukumfarmer.util.TextUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles NPC interactions, owner-only authorization, protection, and chunk load registration.
 */
public class NpcListener implements Listener {

    private final HukumFarmer plugin;
    private final MainMenuGui mainMenuGui;
    private final PurchaseGui purchaseGui;
    private final Map<UUID, Long> interactionDebounce = new ConcurrentHashMap<>();

    public NpcListener(HukumFarmer plugin, MainMenuGui mainMenuGui, PurchaseGui purchaseGui) {
        this.plugin = plugin;
        this.mainMenuGui = mainMenuGui;
        this.purchaseGui = purchaseGui;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        handleInteraction(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        handleInteraction(event.getPlayer(), event.getRightClicked(), event);
    }

    private void handleInteraction(Player player, Entity entity, org.bukkit.event.Cancellable event) {
        if (entity == null) return;

        boolean isNpc = plugin.getNpcManager().isFarmerNpc(entity);
        boolean isHolo = plugin.getNpcManager().isHologram(entity);

        if (!isNpc && !isHolo) {
            return;
        }

        event.setCancelled(true);

        long now = System.currentTimeMillis();
        long lastClick = interactionDebounce.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastClick < 350) {
            return;
        }
        interactionDebounce.put(player.getUniqueId(), now);

        FileConfiguration config = plugin.getConfigManager().getNpcConfig();
        if (!config.getBoolean("npc.enabled", true)) {
            return;
        }

        double maxDist = config.getDouble("npc.interaction-distance", 4.5);
        if (player.getLocation().distanceSquared(entity.getLocation()) > (maxDist * maxDist)) {
            return;
        }

        String npcId = plugin.getNpcManager().getNpcId(entity);
        Optional<Farmer> optFarmer = plugin.getFarmerManager().getFarmerByNpcId(npcId);

        // If this NPC is tied to a specific farmer
        if (optFarmer.isPresent()) {
            Farmer farmer = optFarmer.get();
            boolean ownerOnly = config.getBoolean("npc.owner-only", true);

            if (ownerOnly && !farmer.getOwnerUUID().equals(player.getUniqueId()) && !player.hasPermission("hukumfarmer.admin")) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-not-owner", "%owner%", farmer.getOwnerName()));
                SoundEffectUtil.playEffect(player, plugin.getConfigManager().getMainConfig().getConfigurationSection("effects.error"));
                return;
            }

            playInteractionFeedback(player, config);
            mainMenuGui.open(player, farmer);
            return;
        }

        // Generic admin/global NPC: Open the player's own farmer GUI or Purchase GUI
        Optional<Farmer> playerFarmer = plugin.getFarmerManager().getFarmer(player.getUniqueId());
        if (playerFarmer.isEmpty()) {
            playInteractionFeedback(player, config);
            purchaseGui.open(player);
        } else {
            playInteractionFeedback(player, config);
            mainMenuGui.open(player, playerFarmer.get());
        }
    }

    private void playInteractionFeedback(Player player, FileConfiguration config) {
        if (config.getBoolean("npc.interaction-sound.enabled", true)) {
            String soundName = config.getString("npc.interaction-sound.sound", "ENTITY_VILLAGER_YES");
            float volume = (float) config.getDouble("npc.interaction-sound.volume", 1.0);
            float pitch = (float) config.getDouble("npc.interaction-sound.pitch", 1.0);
            SoundEffectUtil.playSound(player, soundName, volume, pitch);
        }

        if (config.getBoolean("npc.interaction-message.enabled", false)) {
            String msg = config.getString("npc.interaction-message.message", "");
            if (!msg.isEmpty()) {
                player.sendMessage(TextUtil.color(msg.replace("%prefix%", plugin.getLanguageManager().getPrefix(plugin.getLanguageManager().getPlayerLanguage(player.getUniqueId())))));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcDamage(EntityDamageEvent event) {
        if (plugin.getNpcManager().isFarmerNpc(event.getEntity()) || plugin.getNpcManager().isHologram(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcDamageByEntity(EntityDamageByEntityEvent event) {
        if (plugin.getNpcManager().isFarmerNpc(event.getEntity()) || plugin.getNpcManager().isHologram(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcCombust(EntityCombustEvent event) {
        if (plugin.getNpcManager().isFarmerNpc(event.getEntity()) || plugin.getNpcManager().isHologram(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() != null && plugin.getNpcManager().isFarmerNpc(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        plugin.getNpcManager().checkChunkNpcs(event.getChunk());
    }
}
