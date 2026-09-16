package me.hukumcraft.hukumfarmer.hook;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Soft hook into WorldGuard 7.x via reflection to enforce region build permissions
 * without hard-depending on WorldGuard or crashing on servers without it.
 */
public class WorldGuardHook {

    private final HukumFarmer plugin;
    private boolean hooked = false;

    public WorldGuardHook(HukumFarmer plugin) {
        this.plugin = plugin;
        init();
    }

    public void init() {
        Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wg != null && wg.isEnabled()) {
            this.hooked = true;
            plugin.getLogger().info("WorldGuard integration enabled (Region protection active).");
        } else {
            this.hooked = false;
        }
    }

    public boolean isHooked() {
        return hooked;
    }

    /**
     * Checks if a player has permission to build / place a farmer at the given location.
     * Returns true if WorldGuard is not present or if the region query permits building.
     */
    public boolean canBuild(Player player, Location location) {
        if (!hooked || player == null || location == null || location.getWorld() == null) {
            return true;
        }

        // Bypass for server operators or players with full admin bypass
        if (player.isOp() || player.hasPermission("worldguard.region.bypass." + location.getWorld().getName())) {
            return true;
        }

        try {
            // WorldGuard 7.x Reflection
            // com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Method getInstance = wgClass.getMethod("getInstance");
            Object wgInstance = getInstance.invoke(null);

            Method getPlatform = wgInstance.getClass().getMethod("getPlatform");
            Object platform = getPlatform.invoke(wgInstance);

            Method getRegionContainer = platform.getClass().getMethod("getRegionContainer");
            Object container = getRegionContainer.invoke(platform);

            Method createQuery = container.getClass().getMethod("createQuery");
            Object query = createQuery.invoke(container);

            // BukkitAdapter.adapt(location)
            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Method adaptLocation = bukkitAdapterClass.getMethod("adapt", Location.class);
            Object adaptedLocation = adaptLocation.invoke(null, location);

            // WorldGuardPlugin.inst().wrapPlayer(player)
            Class<?> wgPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            Method instMethod = wgPluginClass.getMethod("inst");
            Object wgPluginInst = instMethod.invoke(null);
            Method wrapPlayer = wgPluginInst.getClass().getMethod("wrapPlayer", Player.class);
            Object wrappedPlayer = wrapPlayer.invoke(wgPluginInst, player);

            // Flags.BUILD
            Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            Object buildFlag = flagsClass.getField("BUILD").get(null);

            // RegionQuery#testState(Location, RegionAssociable, StateFlag...)
            Class<?> stateFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Class<?> regionAssociableClass = Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable");
            Class<?> weLocationClass = Class.forName("com.sk89q.worldedit.util.Location");

            Object stateFlagArray = Array.newInstance(stateFlagClass, 1);
            Array.set(stateFlagArray, 0, buildFlag);

            Method testStateMethod = query.getClass().getMethod("testState", weLocationClass, regionAssociableClass, stateFlagArray.getClass());
            Object testResult = testStateMethod.invoke(query, adaptedLocation, wrappedPlayer, stateFlagArray);

            if (testResult instanceof Boolean b) {
                return b;
            }
            return true;
        } catch (Throwable t) {
            if (plugin.getConfigManager().getMainConfig().getBoolean("logging.debug", false)) {
                plugin.getLogger().log(Level.FINE, "WorldGuard canBuild check returned exception, allowing by default", t);
            }
            return true;
        }
    }
}
