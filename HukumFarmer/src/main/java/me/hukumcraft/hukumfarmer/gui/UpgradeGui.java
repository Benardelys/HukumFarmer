package me.hukumcraft.hukumfarmer.gui;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.compatibility.CompatibilityUtil;
import me.hukumcraft.hukumfarmer.message.MessageParser;
import me.hukumcraft.hukumfarmer.model.Farmer;
import me.hukumcraft.hukumfarmer.model.FarmerLevel;
import me.hukumcraft.hukumfarmer.util.ItemBuilder;
import me.hukumcraft.hukumfarmer.util.TextUtil;
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
 * Upgrade GUI displaying level comparison, upgrade costs, and actions.
 */
public class UpgradeGui {

    private final HukumFarmer plugin;

    public UpgradeGui(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Farmer farmer) {
        FileConfiguration guiConfig = plugin.getConfigManager().getGuiConfig();
        ConfigurationSection menuSec = guiConfig.getConfigurationSection("upgrade-menu");
        if (menuSec == null) return;

        int size = menuSec.getInt("size", 36);
        String defaultTitle = menuSec.getString("title", "<aqua><bold>Çiftçi Seviye Yükseltme</bold></aqua>");
        String title = plugin.getLanguageManager().getGuiTitle(player.getUniqueId(), "upgrade-menu", defaultTitle);

        FarmerGuiHolder holder = new FarmerGuiHolder(farmer, GuiType.UPGRADE);
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

        FarmerLevel currentLevel = plugin.getLevelManager().getLevel(farmer.getLevel());
        FarmerLevel nextLevel = plugin.getLevelManager().getLevel(farmer.getLevel() + 1);
        boolean isMax = plugin.getLevelManager().isMaxLevel(farmer.getLevel());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%level%", String.valueOf(farmer.getLevel()));
        placeholders.put("%next_level%", isMax ? "MAX" : String.valueOf(farmer.getLevel() + 1));
        placeholders.put("%max_storage%", TextUtil.formatNumber(currentLevel.getMaxStorage()));
        placeholders.put("%next_max_storage%", isMax ? "MAX" : TextUtil.formatNumber(nextLevel.getMaxStorage()));
        placeholders.put("%multiplier%", String.format("%.2f", currentLevel.getMultiplier()));
        placeholders.put("%next_multiplier%", isMax ? "MAX" : String.format("%.2f", nextLevel.getMultiplier()));
        placeholders.put("%harvest_speed%", String.valueOf(currentLevel.getHarvestSpeedSeconds()));
        placeholders.put("%next_harvest_speed%", isMax ? "MAX" : String.valueOf(nextLevel.getHarvestSpeedSeconds()));
        placeholders.put("%harvest_radius%", String.valueOf(currentLevel.getHarvestRadius()));
        placeholders.put("%next_harvest_radius%", isMax ? "MAX" : String.valueOf(nextLevel.getHarvestRadius()));
        placeholders.put("%upgrade_cost%", isMax ? "0" : TextUtil.formatMoney(currentLevel.getUpgradeCost()));
        placeholders.put("%upgrade_xp%", isMax ? "0" : TextUtil.formatNumber(currentLevel.getUpgradeXp()));

        // Current Level Item
        ConfigurationSection curSec = menuSec.getConfigurationSection("current-level-item");
        if (curSec != null) {
            int slot = curSec.getInt("slot", 11);
            String matName = curSec.getString("material", "EXPERIENCE_BOTTLE");
            Material mat = CompatibilityUtil.matchMaterial(matName).orElse(Material.EXPERIENCE_BOTTLE);

            String rawName = curSec.getString("name", "<yellow><bold>Mevcut Durum</bold></yellow>");
            List<String> rawLore = curSec.getStringList("lore");

            String locName = plugin.getLanguageManager().getGuiItemName(player.getUniqueId(), "upgrade-menu", "current-level-item", rawName);
            List<String> locLore = plugin.getLanguageManager().getGuiItemLore(player.getUniqueId(), "upgrade-menu", "current-level-item", rawLore);

            String name = MessageParser.replace(locName, placeholders);
            List<String> lore = MessageParser.replace(locLore, placeholders);

            ItemStack curItem = ItemBuilder.from(mat)
                    .name(name)
                    .lore(lore)
                    .build();

            inv.setItem(slot, curItem);
        }

        // Upgrade Action Button
        ConfigurationSection upSec = menuSec.getConfigurationSection("upgrade-action-button");
        if (upSec != null) {
            int slot = upSec.getInt("slot", 15);
            String matName = upSec.getString("material", "ANVIL");
            Material mat = CompatibilityUtil.matchMaterial(matName).orElse(Material.ANVIL);

            String rawName = upSec.getString("name", "<green><bold>Yükselt</bold></green>");
            List<String> rawLore = upSec.getStringList("lore");

            String locName = plugin.getLanguageManager().getGuiItemName(player.getUniqueId(), "upgrade-menu", "upgrade-action-button", rawName);
            List<String> locLore = plugin.getLanguageManager().getGuiItemLore(player.getUniqueId(), "upgrade-menu", "upgrade-action-button", rawLore);

            String name = MessageParser.replace(locName, placeholders);
            List<String> lore = MessageParser.replace(locLore, placeholders);

            ItemStack upItem = ItemBuilder.from(mat)
                    .name(name)
                    .lore(lore)
                    .glow()
                    .build();

            inv.setItem(slot, upItem);
            holder.setAction(slot, "UPGRADE");
        }

        // Back Button
        ConfigurationSection backSec = menuSec.getConfigurationSection("back-button");
        if (backSec != null) {
            int slot = backSec.getInt("slot", 31);
            String matName = backSec.getString("material", "ARROW");
            Material mat = CompatibilityUtil.matchMaterial(matName).orElse(Material.ARROW);

            String rawName = backSec.getString("name", "<red><bold>Geri Dön</bold></red>");
            List<String> rawLore = backSec.getStringList("lore");

            String locName = plugin.getLanguageManager().getGuiItemName(player.getUniqueId(), "upgrade-menu", "back-button", rawName);
            List<String> locLore = plugin.getLanguageManager().getGuiItemLore(player.getUniqueId(), "upgrade-menu", "back-button", rawLore);

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
