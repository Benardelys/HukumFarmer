package me.hukumcraft.hukumfarmer.gui;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.compatibility.CompatibilityUtil;
import me.hukumcraft.hukumfarmer.message.MessageParser;
import me.hukumcraft.hukumfarmer.model.Farmer;
import me.hukumcraft.hukumfarmer.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings GUI for toggling automatic behaviors, changing language, and renaming the farmer.
 */
public class SettingsGui {

    private final HukumFarmer plugin;

    public SettingsGui(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Farmer farmer) {
        FileConfiguration guiConfig = plugin.getConfigManager().getGuiConfig();
        ConfigurationSection menuSec = guiConfig.getConfigurationSection("settings-menu");
        if (menuSec == null) return;

        int size = menuSec.getInt("size", 27);
        String defaultTitle = menuSec.getString("title", "<dark_purple><bold>Çiftçi Ayarları</bold></dark_purple>");
        String title = plugin.getLanguageManager().getGuiTitle(player.getUniqueId(), "settings-menu", defaultTitle);

        FarmerGuiHolder holder = new FarmerGuiHolder(farmer, GuiType.SETTINGS);
        Inventory inv = Bukkit.createInventory(holder, size, MessageParser.parse(title));
        holder.setInventory(inv);

        // Fillers
        ConfigurationSection fillerSec = guiConfig.getConfigurationSection("filler-item");
        if (fillerSec != null) {
            String fillerMatName = fillerSec.getString("material", "BLACK_STAINED_GLASS_PANE");
            Material fillerMat = CompatibilityUtil.matchMaterial(fillerMatName).orElse(Material.BLACK_STAINED_GLASS_PANE);
            ItemStack filler = ItemBuilder.from(fillerMat)
                    .name(fillerSec.getString("name", " "))
                    .lore(fillerSec.getStringList("lore"))
                    .build();

            for (int i = 0; i < size; i++) {
                inv.setItem(i, filler);
            }
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%farmer_name%", farmer.getCustomName());
        placeholders.put("%auto_harvest_status%", plugin.getLanguageManager().getStatusString(player.getUniqueId(), farmer.isAutoHarvest()));
        placeholders.put("%auto_sell_status%", plugin.getLanguageManager().getStatusString(player.getUniqueId(), farmer.isAutoSell()));

        // Rename Button
        ConfigurationSection renameSec = menuSec.getConfigurationSection("rename-button");
        if (renameSec != null) {
            int slot = renameSec.getInt("slot", 10);
            String matName = renameSec.getString("material", "NAME_TAG");
            Material mat = CompatibilityUtil.matchMaterial(matName).orElse(Material.NAME_TAG);

            String rawName = renameSec.getString("name", "<yellow><bold>Yeniden Adlandır</bold></yellow>");
            List<String> rawLore = renameSec.getStringList("lore");

            String locName = plugin.getLanguageManager().getGuiItemName(player.getUniqueId(), "settings-menu", "rename-button", rawName);
            List<String> locLore = plugin.getLanguageManager().getGuiItemLore(player.getUniqueId(), "settings-menu", "rename-button", rawLore);

            String name = MessageParser.replace(locName, placeholders);
            List<String> lore = MessageParser.replace(locLore, placeholders);

            ItemStack renameItem = ItemBuilder.from(mat)
                    .name(name)
                    .lore(lore)
                    .build();

            inv.setItem(slot, renameItem);
            holder.setAction(slot, "RENAME");
        }

        // Toggle Auto Harvest
        ConfigurationSection harvestSec = menuSec.getConfigurationSection("toggle-harvest");
        if (harvestSec != null) {
            int slot = harvestSec.getInt("slot", 12);
            String matName = harvestSec.getString("material", "DIAMOND_HOE");
            Material mat = CompatibilityUtil.matchMaterial(matName).orElse(Material.DIAMOND_HOE);

            String rawName = harvestSec.getString("name", "<green><bold>Otomatik Hasat</bold></green>");
            List<String> rawLore = harvestSec.getStringList("lore");

            String locName = plugin.getLanguageManager().getGuiItemName(player.getUniqueId(), "settings-menu", "toggle-harvest", rawName);
            List<String> locLore = plugin.getLanguageManager().getGuiItemLore(player.getUniqueId(), "settings-menu", "toggle-harvest", rawLore);

            String name = MessageParser.replace(locName, placeholders);
            List<String> lore = MessageParser.replace(locLore, placeholders);

            ItemStack harvestItem = ItemBuilder.from(mat)
                    .name(name)
                    .lore(lore)
                    .build();

            inv.setItem(slot, harvestItem);
            holder.setAction(slot, "TOGGLE_HARVEST");
        }

        // Toggle Auto Sell
        ConfigurationSection sellSec = menuSec.getConfigurationSection("toggle-sell");
        if (sellSec != null) {
            int slot = sellSec.getInt("slot", 14);
            String matName = sellSec.getString("material", "EMERALD");
            Material mat = CompatibilityUtil.matchMaterial(matName).orElse(Material.EMERALD);

            String rawName = sellSec.getString("name", "<light_purple><bold>Otomatik Satış</bold></light_purple>");
            List<String> rawLore = sellSec.getStringList("lore");

            String locName = plugin.getLanguageManager().getGuiItemName(player.getUniqueId(), "settings-menu", "toggle-sell", rawName);
            List<String> locLore = plugin.getLanguageManager().getGuiItemLore(player.getUniqueId(), "settings-menu", "toggle-sell", rawLore);

            String name = MessageParser.replace(locName, placeholders);
            List<String> lore = MessageParser.replace(locLore, placeholders);

            ItemStack sellItem = ItemBuilder.from(mat)
                    .name(name)
                    .lore(lore)
                    .build();

            inv.setItem(slot, sellItem);
            holder.setAction(slot, "TOGGLE_SELL");
        }

        // Language Button
        ConfigurationSection langSec = menuSec.getConfigurationSection("language-button");
        if (langSec != null) {
            int slot = langSec.getInt("slot", 16);
            String matName = langSec.getString("material", "BOOK");
            Material mat = CompatibilityUtil.matchMaterial(matName).orElse(Material.BOOK);

            String rawName = langSec.getString("name", "<aqua><bold>🌐 Dil Değiştir</bold></aqua>");
            List<String> rawLore = langSec.getStringList("lore");

            String locName = plugin.getLanguageManager().getGuiItemName(player.getUniqueId(), "settings-menu", "language-button", rawName);
            List<String> locLore = plugin.getLanguageManager().getGuiItemLore(player.getUniqueId(), "settings-menu", "language-button", rawLore);

            ItemStack langItem = ItemBuilder.from(mat)
                    .name(locName)
                    .lore(locLore)
                    .build();

            inv.setItem(slot, langItem);
            holder.setAction(slot, "OPEN_LANGUAGE");
        }

        // Back Button
        ConfigurationSection backSec = menuSec.getConfigurationSection("back-button");
        if (backSec != null) {
            int slot = backSec.getInt("slot", 22);
            String matName = backSec.getString("material", "ARROW");
            Material mat = CompatibilityUtil.matchMaterial(matName).orElse(Material.ARROW);

            String rawName = backSec.getString("name", "<red><bold>Geri Dön</bold></red>");
            List<String> rawLore = backSec.getStringList("lore");

            String locName = plugin.getLanguageManager().getGuiItemName(player.getUniqueId(), "settings-menu", "back-button", rawName);
            List<String> locLore = plugin.getLanguageManager().getGuiItemLore(player.getUniqueId(), "settings-menu", "back-button", rawLore);

            ItemStack backItem = ItemBuilder.from(mat)
                    .name(locName)
                    .lore(locLore)
                    .build();

            inv.setItem(slot, backItem);
            holder.setAction(slot, "BACK");
        }

        player.openInventory(inv);
    }
}
