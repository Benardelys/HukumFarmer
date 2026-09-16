package me.hukumcraft.hukumfarmer.command;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.message.MessageParser;
import me.hukumcraft.hukumfarmer.model.Farmer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Handles all administrative commands for HukumFarmer.
 */
public class FarmerAdminCommand {

    private final HukumFarmer plugin;

    public FarmerAdminCommand(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    public void handle(CommandSender sender, String[] args) {
        Player player = sender instanceof Player p ? p : null;
        if (!sender.hasPermission("hukumfarmer.admin")) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
            return;
        }

        if (args.length < 2) {
            sendAdminHelp(sender);
            return;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "give" -> handleGive(sender, args);
            case "reset" -> handleReset(sender, args);
            case "setlevel" -> handleSetLevel(sender, args);
            case "addxp" -> handleAddXp(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "info" -> handleInfo(sender, args);
            case "language", "lang" -> handleLanguage(sender, args);
            default -> sendAdminHelp(sender);
        }
    }

    private void handleLanguage(CommandSender sender, String[] args) {
        Player player = sender instanceof Player p ? p : null;
        if (!sender.hasPermission("hukumfarmer.admin.language")) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
            return;
        }

        if (args.length < 4) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-usage-language"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "player-not-found"));
            return;
        }

        String langCode = args[3].toLowerCase().trim();
        if (!plugin.getLanguageManager().isSupportedLanguage(langCode)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "language-invalid"));
            return;
        }

        plugin.getLanguageManager().setPlayerLanguage(target.getUniqueId(), langCode);
        plugin.getFarmerRepository().savePlayerLanguageAsync(target.getUniqueId(), langCode);

        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-language-set",
                "%player%", target.getName() != null ? target.getName() : args[2],
                "%language%", langCode.toUpperCase()));

        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(plugin.getLanguageManager().getMessage(target.getPlayer(), "language-changed", "%language%", langCode.toUpperCase()));
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        Player player = sender instanceof Player p ? p : null;
        if (!sender.hasPermission("hukumfarmer.admin.give") && !sender.hasPermission("hukumfarmer.admin")) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-usage-give-egg"));
            return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "player-not-found"));
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1) amount = 1;
                if (amount > 64) amount = 64;
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getLanguageManager().getMessage(player, "invalid-amount"));
                return;
            }
        }

        org.bukkit.inventory.ItemStack egg = plugin.getFarmerItemManager().createFarmerEgg(amount);
        if (plugin.getFarmerItemManager().hasInventorySpace(target, egg)) {
            target.getInventory().addItem(egg);
        } else {
            target.getWorld().dropItemNaturally(target.getLocation(), egg);
        }

        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-egg-given",
                "%player%", target.getName(),
                "%amount%", String.valueOf(amount)));
        target.sendMessage(plugin.getLanguageManager().getMessage(target, "admin-egg-received",
                "%amount%", String.valueOf(amount)));
    }

    private void handleReset(CommandSender sender, String[] args) {
        Player player = sender instanceof Player p ? p : null;
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-usage-reset"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        Optional<Farmer> optFarmer = plugin.getFarmerManager().getFarmer(target.getUniqueId());
        if (optFarmer.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "player-not-found"));
            return;
        }

        Farmer farmer = optFarmer.get();
        farmer.setLevel(1);
        farmer.setXp(0);
        farmer.setEarnings(0);
        farmer.clearStoredCrops();
        farmer.markDirty();
        plugin.getFarmerManager().updateFarmerHologram(farmer);

        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-reset", "%player%", target.getName()));
    }

    private void handleSetLevel(CommandSender sender, String[] args) {
        Player player = sender instanceof Player p ? p : null;
        if (args.length < 4) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-usage-setlevel"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        Optional<Farmer> optFarmer = plugin.getFarmerManager().getFarmer(target.getUniqueId());
        if (optFarmer.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "player-not-found"));
            return;
        }

        try {
            int level = Integer.parseInt(args[3]);
            if (level < 1) {
                sender.sendMessage(plugin.getLanguageManager().getMessage(player, "invalid-level"));
                return;
            }

            Farmer farmer = optFarmer.get();
            farmer.setLevel(level);
            farmer.markDirty();
            plugin.getFarmerManager().updateFarmerHologram(farmer);

            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-setlevel",
                    "%player%", target.getName(),
                    "%level%", String.valueOf(level)));
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "invalid-level"));
        }
    }

    private void handleAddXp(CommandSender sender, String[] args) {
        Player player = sender instanceof Player p ? p : null;
        if (args.length < 4) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-usage-addxp"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        Optional<Farmer> optFarmer = plugin.getFarmerManager().getFarmer(target.getUniqueId());
        if (optFarmer.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "player-not-found"));
            return;
        }

        try {
            long xp = Long.parseLong(args[3]);
            if (xp <= 0) {
                sender.sendMessage(plugin.getLanguageManager().getMessage(player, "invalid-amount"));
                return;
            }

            Farmer farmer = optFarmer.get();
            farmer.addXp(xp);

            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-addxp",
                    "%player%", target.getName(),
                    "%amount%", MessageParser.formatNumber(xp)));
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "invalid-amount"));
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        Player player = sender instanceof Player p ? p : null;
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-usage-remove"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (plugin.getFarmerManager().getFarmer(target.getUniqueId()).isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "player-not-found"));
            return;
        }

        plugin.getFarmerManager().deleteFarmer(target.getUniqueId());
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-removed", "%player%", target.getName()));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        Player player = sender instanceof Player p ? p : null;
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-usage-info"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        Optional<Farmer> optFarmer = plugin.getFarmerManager().getFarmer(target.getUniqueId());
        if (optFarmer.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "player-not-found"));
            return;
        }

        Farmer f = optFarmer.get();
        String enabledStr = plugin.getLanguageManager().getMessage(player, "status-enabled");
        String disabledStr = plugin.getLanguageManager().getMessage(player, "status-disabled");

        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-info-header", "%player%", f.getOwnerName()));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-info-name", "%name%", f.getCustomName()));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-info-level", "%level%", String.valueOf(f.getLevel()), "%xp%", MessageParser.formatNumber(f.getXp())));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-info-storage", "%stored%", MessageParser.formatNumber(f.getTotalStoredItems())));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-info-earnings", "%earnings%", MessageParser.formatMoney(f.getEarnings())));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-info-autoharvest", "%status%", f.isAutoHarvest() ? enabledStr : disabledStr));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-info-autosell", "%status%", f.isAutoSell() ? enabledStr : disabledStr));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-info-harvested", "%harvested%", MessageParser.formatNumber(f.getTotalHarvested())));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-info-totalearnings", "%earnings%", MessageParser.formatMoney(f.getTotalEarnings())));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-info-footer"));
    }

    private void sendAdminHelp(CommandSender sender) {
        Player player = sender instanceof Player p ? p : null;
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-help-header"));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-help-give"));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-help-language"));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-help-reset"));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-help-setlevel"));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-help-addxp"));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-help-remove"));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-help-info"));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "admin-help-footer"));
    }
}
