package me.hukumcraft.hukumfarmer.hook.itemsadder;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Optional integration hook for ItemsAdder custom items.
 * Uses isolated reflection to ensure HukumFarmer never throws ClassNotFoundException or
 * NoClassDefFoundError when ItemsAdder is not installed or enabled.
 */
public class ItemsAdderHook {

    private final HukumFarmer plugin;
    private boolean available = false;
    private Method getInstanceMethod = null;
    private Method getItemStackMethod = null;

    public ItemsAdderHook(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    public void init() {
        if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            this.available = false;
            return;
        }

        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            this.getInstanceMethod = customStackClass.getMethod("getInstance", String.class);
            this.getItemStackMethod = customStackClass.getMethod("getItemStack");
            this.available = true;
            plugin.getLogger().info("Successfully hooked into ItemsAdder for custom items.");
        } catch (Throwable t) {
            this.available = false;
            plugin.getLogger().log(Level.WARNING, "ItemsAdder plugin detected, but could not link CustomStack API: " + t.getMessage());
        }
    }

    public boolean isAvailable() {
        return available && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }

    /**
     * Safely retrieves an ItemStack from ItemsAdder by its namespaced item identifier.
     *
     * @param namespacedId e.g. "hukumfarmer:farmer_egg" or "my_items:seed_bag"
     * @return cloned ItemStack or null if unavailable/invalid.
     */
    public ItemStack getItem(String namespacedId) {
        if (!isAvailable() || namespacedId == null || namespacedId.trim().isEmpty()) {
            return null;
        }

        try {
            Object customStack = getInstanceMethod.invoke(null, namespacedId.trim());
            if (customStack != null) {
                Object itemObj = getItemStackMethod.invoke(customStack);
                if (itemObj instanceof ItemStack itemStack) {
                    return itemStack.clone();
                }
            }
        } catch (Throwable t) {
            if (plugin.getConfigManager().getMainConfig().getBoolean("logging.debug", false)) {
                plugin.getLogger().warning("Failed to retrieve ItemsAdder item '" + namespacedId + "': " + t.getMessage());
            }
        }
        return null;
    }
}
