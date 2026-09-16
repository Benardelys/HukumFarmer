package me.hukumcraft.hukumfarmer.hook.axminions;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Safe runtime detector and hook for AxMinions.
 */
public class AxMinionsHook {

    private final HukumFarmer plugin;
    private boolean hooked = false;

    public AxMinionsHook(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("integrations.axminions.enabled", true)) {
            hooked = false;
            return false;
        }

        Plugin axMinionsPlugin = Bukkit.getPluginManager().getPlugin("AxMinions");
        if (axMinionsPlugin == null || !axMinionsPlugin.isEnabled()) {
            plugin.getLogger().info("AxMinions not detected. Continuing with standalone mode.");
            hooked = false;
            return false;
        }

        hooked = true;
        plugin.getLogger().info("Successfully hooked into AxMinions (v" + axMinionsPlugin.getDescription().getVersion() + ") integration!");
        return true;
    }

    public boolean isHooked() {
        return hooked;
    }
}
