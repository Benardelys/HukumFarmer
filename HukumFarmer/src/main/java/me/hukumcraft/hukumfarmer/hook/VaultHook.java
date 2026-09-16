package me.hukumcraft.hukumfarmer.hook;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Level;

/**
 * Hook for Vault Economy API with fallback handling.
 */
public class VaultHook {

    private final HukumFarmer plugin;
    private Economy economy = null;
    private boolean hooked = false;

    public VaultHook(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault plugin not found! Economy features will be disabled.");
            hooked = false;
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("No compatible Economy provider found for Vault! Economy features will be disabled.");
            hooked = false;
            return false;
        }

        economy = rsp.getProvider();
        hooked = (economy != null);
        if (hooked) {
            plugin.getLogger().info("Successfully hooked into Vault Economy (" + economy.getName() + ").");
        }
        return hooked;
    }

    public boolean isHooked() {
        return hooked && economy != null;
    }

    public double getBalance(OfflinePlayer player) {
        if (!isHooked() || player == null) return 0.0;
        try {
            return economy.getBalance(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get balance for " + player.getName(), e);
            return 0.0;
        }
    }

    public boolean hasEnough(OfflinePlayer player, double amount) {
        if (!isHooked() || player == null || amount <= 0) return true;
        return getBalance(player) >= amount;
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!isHooked() || player == null || amount <= 0) return true;
        try {
            EconomyResponse response = economy.withdrawPlayer(player, amount);
            return response.transactionSuccess();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to withdraw money from " + player.getName(), e);
            return false;
        }
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (!isHooked() || player == null || amount <= 0) return true;
        try {
            EconomyResponse response = economy.depositPlayer(player, amount);
            return response.transactionSuccess();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to deposit money to " + player.getName(), e);
            return false;
        }
    }
}
