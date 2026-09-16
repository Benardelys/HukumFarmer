package me.hukumcraft.hukumfarmer.command;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.gui.*;
import me.hukumcraft.hukumfarmer.message.MessageParser;
import me.hukumcraft.hukumfarmer.model.CropConfig;
import me.hukumcraft.hukumfarmer.model.Farmer;
import me.hukumcraft.hukumfarmer.model.FarmerLevel;
import me.hukumcraft.hukumfarmer.util.SoundEffectUtil;
import me.hukumcraft.hukumfarmer.util.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;

/**
 * Main command executor for /farmer, /çiftçi, /ciftci.
 */
public class FarmerCommand implements CommandExecutor {

    private final HukumFarmer plugin;
    private final MainMenuGui mainMenuGui;
    private final StorageGui storageGui;
    private final UpgradeGui upgradeGui;
    private final SettingsGui settingsGui;
    private final PurchaseGui purchaseGui;
    private final LanguageGui languageGui;
    private final FarmerAdminCommand adminCommand;
    private final FarmerNpcCommand npcCommand;

    public FarmerCommand(HukumFarmer plugin, MainMenuGui mainMenuGui, StorageGui storageGui,
                         UpgradeGui upgradeGui, SettingsGui settingsGui,
                         PurchaseGui purchaseGui, LanguageGui languageGui,
                         FarmerAdminCommand adminCommand, FarmerNpcCommand npcCommand) {
        this.plugin = plugin;
        this.mainMenuGui = mainMenuGui;
        this.storageGui = storageGui;
        this.upgradeGui = upgradeGui;
        this.settingsGui = settingsGui;
        this.purchaseGui = purchaseGui;
        this.languageGui = languageGui;
        this.adminCommand = adminCommand;
        this.npcCommand = npcCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getLanguageManager().getMessage((Player) null, "player-only"));
                return true;
            }
            openMainOrPurchase(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help", "yardim" -> handleHelp(sender, label);
            case "create", "olustur", "buy", "satinal" -> handleCreateOrPurchase(sender);
            case "menu" -> handleMenu(sender);
            case "language", "lang", "dil" -> handleLanguage(sender);
            case "storage", "depo" -> handleStorage(sender);
            case "upgrade", "yukselt" -> handleUpgrade(sender);
            case "sell", "sat" -> handleSell(sender);
            case "stats", "istatistik" -> handleStats(sender);
            case "settings", "ayarlar" -> handleSettings(sender);
            case "reload", "yenile" -> handleReload(sender);
            case "admin", "yonetici" -> adminCommand.handle(sender, args);
            case "npc" -> npcCommand.handle(sender, args);
            default -> {
                if (sender instanceof Player player) {
                    openMainOrPurchase(player);
                } else {
                    handleHelp(sender, label);
                }
            }
        }
        return true;
    }

    private void openMainOrPurchase(Player player) {
        if (!player.hasPermission("hukumfarmer.use")) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
            return;
        }

        Optional<Farmer> opt = plugin.getFarmerManager().getFarmer(player.getUniqueId());
        if (opt.isEmpty()) {
            // New Requirement: Open Purchase GUI
            purchaseGui.open(player);
            return;
        }

        mainMenuGui.open(player, opt.get());
    }

    private void handleCreateOrPurchase(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage((Player) null, "player-only"));
            return;
        }

        if (!player.hasPermission("hukumfarmer.create")) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
            return;
        }

        int maxLimit = plugin.getConfigManager().getMainConfig().getInt("farmer.max-per-player", 1);
        if (plugin.getFarmerManager().getFarmer(player.getUniqueId()).isPresent() && maxLimit <= 1) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-already-exists"));
            return;
        }

        purchaseGui.open(player);
    }

    private void handleLanguage(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage((Player) null, "player-only"));
            return;
        }
        Optional<Farmer> opt = plugin.getFarmerManager().getFarmer(player.getUniqueId());
        languageGui.open(player, opt.orElse(null));
    }

    private void handleMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage((Player) null, "player-only"));
            return;
        }
        openMainOrPurchase(player);
    }

    private void handleStorage(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage((Player) null, "player-only"));
            return;
        }

        if (!player.hasPermission("hukumfarmer.storage")) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
            return;
        }

        Optional<Farmer> opt = plugin.getFarmerManager().getFarmer(player.getUniqueId());
        if (opt.isEmpty()) {
            purchaseGui.open(player);
            return;
        }

        storageGui.open(player, opt.get());
    }

    private void handleUpgrade(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage((Player) null, "player-only"));
            return;
        }

        if (!player.hasPermission("hukumfarmer.upgrade")) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
            return;
        }

        Optional<Farmer> opt = plugin.getFarmerManager().getFarmer(player.getUniqueId());
        if (opt.isEmpty()) {
            purchaseGui.open(player);
            return;
        }

        upgradeGui.open(player, opt.get());
    }

    private void handleSell(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage((Player) null, "player-only"));
            return;
        }

        if (!player.hasPermission("hukumfarmer.sell")) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
            return;
        }

        Optional<Farmer> opt = plugin.getFarmerManager().getFarmer(player.getUniqueId());
        if (opt.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-not-found"));
            return;
        }

        if (!plugin.getVaultHook().isHooked()) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "vault-disabled-warning"));
            return;
        }

        Farmer farmer = opt.get();
        FarmerLevel level = plugin.getLevelManager().getLevel(farmer.getLevel());
        double totalEarned = 0.0;
        long totalSold = 0;

        for (Map.Entry<String, Long> entry : farmer.getStoredCrops().entrySet()) {
            CropConfig crop = plugin.getCropManager().getCrop(entry.getKey()).orElse(null);
            if (crop != null && entry.getValue() > 0) {
                long count = entry.getValue();
                double price = count * crop.getSellPrice() * level.getMultiplier();
                farmer.removeCrop(crop.getId(), count);
                totalEarned += price;
                totalSold += count;
            }
        }

        if (totalSold > 0) {
            plugin.getVaultHook().deposit(player, totalEarned);
            farmer.setTotalEarnings(farmer.getTotalEarnings() + totalEarned);
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "all-crops-sold",
                    "%amount%", TextUtil.formatNumber(totalSold),
                    "%money%", TextUtil.formatMoney(totalEarned)));
            SoundEffectUtil.playEffect(player, plugin.getConfigManager().getMainConfig().getConfigurationSection("effects.crop-sold"));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "not-enough-crops"));
        }
    }

    private void handleStats(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage((Player) null, "player-only"));
            return;
        }

        if (!player.hasPermission("hukumfarmer.stats")) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
            return;
        }

        Optional<Farmer> opt = plugin.getFarmerManager().getFarmer(player.getUniqueId());
        if (opt.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-not-found"));
            return;
        }

        Farmer f = opt.get();
        FarmerLevel level = plugin.getLevelManager().getLevel(f.getLevel());
        player.sendMessage(plugin.getLanguageManager().getMessage(player, "stats-header", "%name%", f.getCustomName()));
        player.sendMessage(plugin.getLanguageManager().getMessage(player, "stats-level", "%level%", String.valueOf(f.getLevel()), "%xp%", MessageParser.formatNumber(f.getXp())));
        player.sendMessage(plugin.getLanguageManager().getMessage(player, "stats-multiplier", "%multiplier%", String.format("%.2f", level.getMultiplier())));
        player.sendMessage(plugin.getLanguageManager().getMessage(player, "stats-storage", "%stored%", MessageParser.formatNumber(f.getTotalStoredItems()), "%max%", MessageParser.formatNumber(level.getMaxStorage())));
        player.sendMessage(plugin.getLanguageManager().getMessage(player, "stats-harvested", "%harvested%", MessageParser.formatNumber(f.getTotalHarvested())));
        player.sendMessage(plugin.getLanguageManager().getMessage(player, "stats-earnings", "%earnings%", MessageParser.formatMoney(f.getTotalEarnings())));
        player.sendMessage(plugin.getLanguageManager().getMessage(player, "stats-footer"));
    }

    private void handleSettings(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage((Player) null, "player-only"));
            return;
        }

        if (!player.hasPermission("hukumfarmer.settings")) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
            return;
        }

        Optional<Farmer> opt = plugin.getFarmerManager().getFarmer(player.getUniqueId());
        if (opt.isEmpty()) {
            purchaseGui.open(player);
            return;
        }

        settingsGui.open(player, opt.get());
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("hukumfarmer.reload")) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(sender instanceof Player p ? p : null, "no-permission"));
            return;
        }

        plugin.reload();
        sender.sendMessage(plugin.getLanguageManager().getMessage(sender instanceof Player p ? p : null, "reloaded"));
    }

    private void handleHelp(CommandSender sender, String label) {
        Player player = sender instanceof Player p ? p : null;
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "help-header"));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "help-command", "%cmd%", "", "%desc%", plugin.getLanguageManager().getMessage(player, "help-desc-main")));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "help-command", "%cmd%", "create", "%desc%", plugin.getLanguageManager().getMessage(player, "help-desc-create")));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "help-command", "%cmd%", "language", "%desc%", plugin.getLanguageManager().getMessage(player, "help-desc-language")));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "help-command", "%cmd%", "storage", "%desc%", plugin.getLanguageManager().getMessage(player, "help-desc-storage")));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "help-command", "%cmd%", "upgrade", "%desc%", plugin.getLanguageManager().getMessage(player, "help-desc-upgrade")));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "help-command", "%cmd%", "sell", "%desc%", plugin.getLanguageManager().getMessage(player, "help-desc-sell")));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "help-command", "%cmd%", "stats", "%desc%", plugin.getLanguageManager().getMessage(player, "help-desc-stats")));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "help-command", "%cmd%", "settings", "%desc%", plugin.getLanguageManager().getMessage(player, "help-desc-settings")));

        if (sender.hasPermission("hukumfarmer.npc")) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "help-command", "%cmd%", "npc", "%desc%", plugin.getLanguageManager().getMessage(player, "help-desc-npc")));
        }
        if (sender.hasPermission("hukumfarmer.reload")) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "help-command", "%cmd%", "reload", "%desc%", plugin.getLanguageManager().getMessage(player, "help-desc-reload")));
        }
        if (sender.hasPermission("hukumfarmer.admin")) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "help-command", "%cmd%", "admin", "%desc%", plugin.getLanguageManager().getMessage(player, "help-desc-admin")));
        }
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "help-footer"));
    }
}
