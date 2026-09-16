package me.hukumcraft.hukumfarmer.security;

import me.hukumcraft.hukumfarmer.HukumFarmer;

import java.util.logging.Level;

/**
 * Monitors and logs suspicious events, data anomalies, and potential exploit attempts.
 */
public class SecurityManager {

    private final HukumFarmer plugin;

    public SecurityManager(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    public void logSecurityEvent(String eventType, String player, String details) {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("security.logging.enabled", true)) {
            return;
        }

        plugin.getLogger().log(Level.WARNING, String.format(
                "[SECURITY] Type: %s | Player: %s | Details: %s",
                eventType,
                (player != null ? player : "SYSTEM"),
                details
        ));
    }
}
