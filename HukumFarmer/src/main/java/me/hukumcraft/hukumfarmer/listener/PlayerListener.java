package me.hukumcraft.hukumfarmer.listener;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.model.Farmer;
import me.hukumcraft.hukumfarmer.util.TextUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player join, quit, language cache sync, and chat rename interactions.
 */
public class PlayerListener implements Listener {

    private final HukumFarmer plugin;

    public PlayerListener(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Load and cache language preference
        plugin.getFarmerRepository().loadAllPlayerLanguagesAsync().thenAccept(langMap -> {
            String savedLang = langMap.get(player.getUniqueId());
            if (savedLang != null) {
                plugin.getLanguageManager().setPlayerLanguage(player.getUniqueId(), savedLang);
            }
        });

        // Update owner name if player has changed their name
        plugin.getFarmerManager().getFarmer(player.getUniqueId()).ifPresent(farmer -> {
            if (!player.getName().equals(farmer.getOwnerName())) {
                farmer.setOwnerName(player.getName());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getFarmerManager().cancelRenamePrompt(player.getUniqueId());
        plugin.getFarmerManager().releaseTransactionLock(player.getUniqueId());

        // Asynchronously save if dirty
        plugin.getFarmerManager().getFarmer(player.getUniqueId()).ifPresent(farmer -> {
            if (farmer.isDirty()) {
                plugin.getFarmerRepository().saveFarmerAsync(farmer);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getFarmerManager().isRenaming(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        plugin.getFarmerManager().cancelRenamePrompt(player.getUniqueId());

        String message = event.getMessage().trim();
        if (message.equalsIgnoreCase("iptal") || message.equalsIgnoreCase("cancel")) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "rename-cancelled"));
            return;
        }

        int maxLength = plugin.getConfigManager().getMainConfig().getInt("farmer.max-name-length", 24);
        if (message.length() > maxLength) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-name-too-long", "%max%", String.valueOf(maxLength)));
            return;
        }

        Farmer farmer = plugin.getFarmerManager().getFarmer(player.getUniqueId()).orElse(null);
        if (farmer == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-not-found"));
            return;
        }

        String cleanName = me.hukumcraft.hukumfarmer.message.MessageParser.stripTags(message);
        farmer.setCustomName(cleanName);
        player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-renamed", "%name%", cleanName));

        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getFarmerManager().updateFarmerHologram(farmer);
        });
    }
}
