package me.hukumcraft.hukumfarmer.listener;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.gui.*;
import me.hukumcraft.hukumfarmer.model.CropConfig;
import me.hukumcraft.hukumfarmer.model.Farmer;
import me.hukumcraft.hukumfarmer.model.FarmerLevel;
import me.hukumcraft.hukumfarmer.util.SoundEffectUtil;
import me.hukumcraft.hukumfarmer.util.TextUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Robust Inventory Listener preventing item duplication, shift/drag exploits,
 * and handling menu interactions for all Farmer GUIs.
 */
public class InventoryListener implements Listener {

    private final HukumFarmer plugin;
    private final MainMenuGui mainMenuGui;
    private final StorageGui storageGui;
    private final UpgradeGui upgradeGui;
    private final SettingsGui settingsGui;
    private final PurchaseGui purchaseGui;
    private final LanguageGui languageGui;

    public InventoryListener(HukumFarmer plugin, MainMenuGui mainMenuGui, StorageGui storageGui,
                             UpgradeGui upgradeGui, SettingsGui settingsGui,
                             PurchaseGui purchaseGui, LanguageGui languageGui) {
        this.plugin = plugin;
        this.mainMenuGui = mainMenuGui;
        this.storageGui = storageGui;
        this.upgradeGui = upgradeGui;
        this.settingsGui = settingsGui;
        this.purchaseGui = purchaseGui;
        this.languageGui = languageGui;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof FarmerGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreativeClick(InventoryCreativeEvent event) {
        if (event.getInventory().getHolder() instanceof FarmerGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof FarmerGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof FarmerGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof FarmerGuiHolder holder)) {
            return;
        }

        // Always cancel any interaction inside farmer menus to prevent taking/duping items
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // If player clicked outside top inventory, still keep cancelled
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getInventory()) {
            return;
        }

        int slot = event.getSlot();
        String action = holder.getAction(slot);
        if (action == null) return;

        Farmer farmer = holder.getFarmer();
        GuiType type = holder.getGuiType();

        playGuiClickSound(player);

        switch (type) {
            case PURCHASE -> handlePurchaseClick(player, action);
            case LANGUAGE -> handleLanguageClick(player, farmer, action);
            case MAIN_MENU -> handleMainMenuClick(player, farmer, action);
            case STORAGE -> handleStorageClick(player, farmer, action, event.getClick());
            case UPGRADE -> handleUpgradeClick(player, farmer, action);
            case SETTINGS -> handleSettingsClick(player, farmer, action);
            default -> {}
        }
    }

    private void handlePurchaseClick(Player player, String action) {
        switch (action) {
            case "CONFIRM_PURCHASE" -> {
                boolean success = plugin.getFarmerManager().processFarmerPurchase(player);
                if (success) {
                    SoundEffectUtil.playEffect(player, plugin.getConfigManager().getMainConfig().getConfigurationSection("effects.farmer-created"));
                } else {
                    SoundEffectUtil.playEffect(player, plugin.getConfigManager().getMainConfig().getConfigurationSection("effects.error"));
                }
                player.closeInventory();
            }
            case "CANCEL" -> player.closeInventory();
            case "OPEN_LANGUAGE" -> languageGui.open(player, null);
        }
    }

    private void handleLanguageClick(Player player, Farmer farmer, String action) {
        if ("BACK".equals(action)) {
            if (farmer != null) {
                mainMenuGui.open(player, farmer);
            } else {
                purchaseGui.open(player);
            }
            return;
        }

        if (action.startsWith("LANG:")) {
            String langCode = action.substring(5).toLowerCase();
            if (plugin.getLanguageManager().isSupportedLanguage(langCode)) {
                plugin.getLanguageManager().setPlayerLanguage(player.getUniqueId(), langCode);
                plugin.getFarmerRepository().savePlayerLanguageAsync(player.getUniqueId(), langCode);
                player.sendMessage(plugin.getLanguageManager().getMessage(player, "language-changed", "%language%", langCode.toUpperCase()));

                // Immediately refresh Language GUI to show active glow & translated title/buttons
                languageGui.open(player, farmer);
            }
        }
    }

    private void handleMainMenuClick(Player player, Farmer farmer, String action) {
        if (farmer == null) return;

        switch (action) {
            case "storage-button" -> storageGui.open(player, farmer);
            case "upgrade-button" -> upgradeGui.open(player, farmer);
            case "language-button" -> languageGui.open(player, farmer);
            case "collect-money" -> {
                double earnings = farmer.collectEarnings();
                if (earnings > 0) {
                    if (plugin.getVaultHook().isHooked()) {
                        plugin.getVaultHook().deposit(player, earnings);
                    }
                    player.sendMessage(plugin.getLanguageManager().getMessage(player, "money-collected", "%amount%", TextUtil.formatMoney(earnings)));
                    SoundEffectUtil.playEffect(player, plugin.getConfigManager().getMainConfig().getConfigurationSection("effects.money-collected"));
                } else {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-money-to-collect"));
                }
                mainMenuGui.open(player, farmer);
            }
            case "auto-harvest-toggle" -> {
                if (!player.hasPermission("hukumfarmer.autoharvest")) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
                    return;
                }
                farmer.setAutoHarvest(!farmer.isAutoHarvest());
                player.sendMessage(plugin.getLanguageManager().getMessage(player, farmer.isAutoHarvest() ? "toggle-auto-harvest-on" : "toggle-auto-harvest-off"));
                mainMenuGui.open(player, farmer);
            }
            case "auto-sell-toggle" -> {
                if (!player.hasPermission("hukumfarmer.autosell")) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
                    return;
                }
                farmer.setAutoSell(!farmer.isAutoSell());
                player.sendMessage(plugin.getLanguageManager().getMessage(player, farmer.isAutoSell() ? "toggle-auto-sell-on" : "toggle-auto-sell-off"));
                mainMenuGui.open(player, farmer);
            }
            case "stats-button", "farmer-info" -> mainMenuGui.open(player, farmer);
            case "close-button" -> player.closeInventory();
        }
    }

    private void handleStorageClick(Player player, Farmer farmer, String action, ClickType clickType) {
        if (farmer == null) return;

        if ("BACK".equals(action)) {
            mainMenuGui.open(player, farmer);
            return;
        }

        if ("SELL_ALL".equals(action)) {
            if (!player.hasPermission("hukumfarmer.sell")) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
                return;
            }
            if (!plugin.getVaultHook().isHooked()) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player, "vault-disabled-warning"));
                return;
            }

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
            storageGui.open(player, farmer);
            return;
        }

        if (action.startsWith("CROP:")) {
            String cropId = action.substring(5);
            CropConfig crop = plugin.getCropManager().getCrop(cropId).orElse(null);
            if (crop == null) return;

            long stored = farmer.getCropAmount(cropId);
            if (stored <= 0) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player, "not-enough-crops"));
                return;
            }

            FarmerLevel level = plugin.getLevelManager().getLevel(farmer.getLevel());

            // Shift + Left Click: Withdraw 64 items to player inventory
            if (clickType.isShiftClick()) {
                int toWithdraw = (int) Math.min(64, stored);
                ItemStack item = new ItemStack(crop.getItemMaterial(), toWithdraw);
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                int actuallyAdded = toWithdraw;
                if (!overflow.isEmpty()) {
                    for (ItemStack drop : overflow.values()) {
                        actuallyAdded -= drop.getAmount();
                        if (plugin.getConfigManager().getStorageConfig().getBoolean("storage.drop-excess-on-ground", true)) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                            actuallyAdded += drop.getAmount();
                        }
                    }
                }
                farmer.removeCrop(cropId, actuallyAdded);
                storageGui.open(player, farmer);
                return;
            }

            // Right click: Sell ALL of this crop
            if (clickType.isRightClick()) {
                if (!player.hasPermission("hukumfarmer.sell")) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
                    return;
                }
                if (!plugin.getVaultHook().isHooked()) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player, "vault-disabled-warning"));
                    return;
                }

                long count = farmer.removeCrop(cropId, stored);
                double earned = count * crop.getSellPrice() * level.getMultiplier();
                plugin.getVaultHook().deposit(player, earned);
                farmer.setTotalEarnings(farmer.getTotalEarnings() + earned);

                player.sendMessage(plugin.getLanguageManager().getMessage(player, "crop-sold",
                        "%amount%", TextUtil.formatNumber(count),
                        "%crop%", crop.getDisplayName(),
                        "%money%", TextUtil.formatMoney(earned)));
                SoundEffectUtil.playEffect(player, plugin.getConfigManager().getMainConfig().getConfigurationSection("effects.crop-sold"));
                storageGui.open(player, farmer);
                return;
            }

            // Left click: Sell 64 of this crop
            if (clickType.isLeftClick()) {
                if (!player.hasPermission("hukumfarmer.sell")) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
                    return;
                }
                if (!plugin.getVaultHook().isHooked()) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player, "vault-disabled-warning"));
                    return;
                }

                long count = farmer.removeCrop(cropId, Math.min(64, stored));
                double earned = count * crop.getSellPrice() * level.getMultiplier();
                plugin.getVaultHook().deposit(player, earned);
                farmer.setTotalEarnings(farmer.getTotalEarnings() + earned);

                player.sendMessage(plugin.getLanguageManager().getMessage(player, "crop-sold",
                        "%amount%", TextUtil.formatNumber(count),
                        "%crop%", crop.getDisplayName(),
                        "%money%", TextUtil.formatMoney(earned)));
                SoundEffectUtil.playEffect(player, plugin.getConfigManager().getMainConfig().getConfigurationSection("effects.crop-sold"));
                storageGui.open(player, farmer);
            }
        }
    }

    private void handleUpgradeClick(Player player, Farmer farmer, String action) {
        if (farmer == null) return;

        if ("BACK".equals(action)) {
            mainMenuGui.open(player, farmer);
            return;
        }

        if ("UPGRADE".equals(action)) {
            if (!player.hasPermission("hukumfarmer.upgrade")) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
                return;
            }

            int currentLvl = farmer.getLevel();
            if (plugin.getLevelManager().isMaxLevel(currentLvl)) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-max-level"));
                return;
            }

            FarmerLevel levelInfo = plugin.getLevelManager().getLevel(currentLvl);

            // Check XP
            if (farmer.getXp() < levelInfo.getUpgradeXp()) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player, "not-enough-xp-upgrade",
                        "%xp%", TextUtil.formatNumber(levelInfo.getUpgradeXp() - farmer.getXp())));
                SoundEffectUtil.playEffect(player, plugin.getConfigManager().getMainConfig().getConfigurationSection("effects.error"));
                return;
            }

            // Check Money
            if (levelInfo.getUpgradeCost() > 0 && plugin.getVaultHook().isHooked()) {
                if (!plugin.getVaultHook().hasEnough(player, levelInfo.getUpgradeCost())) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player, "not-enough-money-upgrade",
                            "%cost%", TextUtil.formatMoney(levelInfo.getUpgradeCost())));
                    SoundEffectUtil.playEffect(player, plugin.getConfigManager().getMainConfig().getConfigurationSection("effects.error"));
                    return;
                }
                plugin.getVaultHook().withdraw(player, levelInfo.getUpgradeCost());
            }

            farmer.setXp(farmer.getXp() - levelInfo.getUpgradeXp());
            farmer.setLevel(currentLvl + 1);
            plugin.getFarmerManager().updateFarmerHologram(farmer);

            player.sendMessage(plugin.getLanguageManager().getMessage(player, "farmer-upgraded", "%level%", String.valueOf(farmer.getLevel())));
            SoundEffectUtil.playEffect(player, plugin.getConfigManager().getMainConfig().getConfigurationSection("effects.farmer-upgraded"));

            upgradeGui.open(player, farmer);
        }
    }

    private void handleSettingsClick(Player player, Farmer farmer, String action) {
        if (farmer == null) return;

        switch (action) {
            case "BACK" -> mainMenuGui.open(player, farmer);
            case "OPEN_LANGUAGE" -> languageGui.open(player, farmer);
            case "TOGGLE_HARVEST" -> {
                if (!player.hasPermission("hukumfarmer.autoharvest")) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
                    return;
                }
                farmer.setAutoHarvest(!farmer.isAutoHarvest());
                player.sendMessage(plugin.getLanguageManager().getMessage(player, farmer.isAutoHarvest() ? "toggle-auto-harvest-on" : "toggle-auto-harvest-off"));
                settingsGui.open(player, farmer);
            }
            case "TOGGLE_SELL" -> {
                if (!player.hasPermission("hukumfarmer.autosell")) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
                    return;
                }
                farmer.setAutoSell(!farmer.isAutoSell());
                player.sendMessage(plugin.getLanguageManager().getMessage(player, farmer.isAutoSell() ? "toggle-auto-sell-on" : "toggle-auto-sell-off"));
                settingsGui.open(player, farmer);
            }
            case "RENAME" -> {
                if (!player.hasPermission("hukumfarmer.rename")) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
                    return;
                }
                player.closeInventory();
                plugin.getFarmerManager().startRenamePrompt(player.getUniqueId());
                player.sendMessage(plugin.getLanguageManager().getMessage(player, "rename-prompt"));
            }
        }
    }

    private void playGuiClickSound(Player player) {
        FileConfiguration config = plugin.getConfigManager().getMainConfig();
        String soundName = config.getString("effects.gui-click.sound", "UI_BUTTON_CLICK");
        float volume = (float) config.getDouble("effects.gui-click.volume", 0.6);
        float pitch = (float) config.getDouble("effects.gui-click.pitch", 1.0);
        SoundEffectUtil.playSound(player, soundName, volume, pitch);
    }
}
