package me.hukumcraft.hukumfarmer.gui;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.compatibility.CompatibilityUtil;
import me.hukumcraft.hukumfarmer.message.MessageParser;
import me.hukumcraft.hukumfarmer.model.CropConfig;
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
 * Builds and opens the Main Farmer Menu GUI.
 */
public class MainMenuGui {

    private final HukumFarmer plugin;

    public MainMenuGui(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Farmer farmer) {
        FileConfiguration guiConfig = plugin.getConfigManager().getGuiConfig();
        ConfigurationSection menuSec = guiConfig.getConfigurationSection("main-menu");
        if (menuSec == null) return;

        int size = menuSec.getInt("size", 45);
        String defaultTitle = menuSec.getString("title", "<gold><bold>Çiftçi Yönetim Paneli</bold></gold>");
        String title = plugin.getLanguageManager().getGuiTitle(player.getUniqueId(), "main-menu", defaultTitle);

        FarmerGuiHolder holder = new FarmerGuiHolder(farmer, GuiType.MAIN_MENU);
        Inventory inv = Bukkit.createInventory(holder, size, MessageParser.parse(title));
        holder.setInventory(inv);

        // Populate Fillers
        ConfigurationSection fillerSec = guiConfig.getConfigurationSection("filler-item");
        if (fillerSec != null) {
            String fillerMatName = fillerSec.getString("material", "BLACK_STAINED_GLASS_PANE");
            Material fillerMat = CompatibilityUtil.matchMaterial(fillerMatName).orElse(Material.BLACK_STAINED_GLASS_PANE);
            ItemStack filler = ItemBuilder.from(fillerMat)
                    .name(fillerSec.getString("name", " "))
                    .lore(fillerSec.getStringList("lore"))
                    .customModelData(fillerSec.getInt("custom-model-data", 0))
                    .build();

            for (int i = 0; i < size; i++) {
                inv.setItem(i, filler);
            }
        }

        FarmerLevel currentLevel = plugin.getLevelManager().getLevel(farmer.getLevel());
        FarmerLevel nextLevel = plugin.getLevelManager().getLevel(farmer.getLevel() + 1);

        double storageValue = calculateStorageValue(farmer);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%farmer_name%", farmer.getCustomName());
        placeholders.put("%owner%", farmer.getOwnerName());
        placeholders.put("%level%", String.valueOf(farmer.getLevel()));
        placeholders.put("%next_level%", String.valueOf(farmer.getLevel() + 1));
        placeholders.put("%xp%", TextUtil.formatNumber(farmer.getXp()));
        placeholders.put("%required_xp%", TextUtil.formatNumber(nextLevel.getUpgradeXp()));
        placeholders.put("%current_storage%", TextUtil.formatNumber(farmer.getTotalStoredItems()));
        placeholders.put("%max_storage%", TextUtil.formatNumber(currentLevel.getMaxStorage()));
        placeholders.put("%multiplier%", String.format("%.2f", currentLevel.getMultiplier()));
        placeholders.put("%earnings%", TextUtil.formatMoney(farmer.getEarnings()));
        placeholders.put("%storage_value%", TextUtil.formatMoney(storageValue));
        placeholders.put("%upgrade_cost%", TextUtil.formatMoney(currentLevel.getUpgradeCost()));
        placeholders.put("%upgrade_xp%", TextUtil.formatNumber(currentLevel.getUpgradeXp()));
        placeholders.put("%auto_harvest_status%", plugin.getLanguageManager().getStatusString(player.getUniqueId(), farmer.isAutoHarvest()));
        placeholders.put("%auto_sell_status%", plugin.getLanguageManager().getStatusString(player.getUniqueId(), farmer.isAutoSell()));
        placeholders.put("%total_harvested%", TextUtil.formatNumber(farmer.getTotalHarvested()));
        placeholders.put("%total_earnings%", TextUtil.formatMoney(farmer.getTotalEarnings()));

        ConfigurationSection itemsSec = menuSec.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                ConfigurationSection itemSec = itemsSec.getConfigurationSection(key);
                if (itemSec == null) continue;

                int slot = itemSec.getInt("slot", 0);
                if (slot < 0 || slot >= size) continue;

                String matName = itemSec.getString("material", "STONE");
                Material mat = CompatibilityUtil.matchMaterial(matName).orElse(Material.STONE);

                String rawName = itemSec.getString("name", "");
                List<String> rawLore = itemSec.getStringList("lore");

                String locName = plugin.getLanguageManager().getGuiItemName(player.getUniqueId(), "main-menu", key, rawName);
                List<String> locLore = plugin.getLanguageManager().getGuiItemLore(player.getUniqueId(), "main-menu", key, rawLore);

                String name = MessageParser.replace(locName, placeholders);
                List<String> lore = MessageParser.replace(locLore, placeholders);
                int cmd = itemSec.getInt("custom-model-data", 0);

                ItemBuilder builder = ItemBuilder.from(mat)
                        .name(name)
                        .lore(lore)
                        .customModelData(cmd);

                if (mat == Material.PLAYER_HEAD) {
                    builder.skullOwner(farmer.getOwnerName());
                }

                inv.setItem(slot, builder.build());
                holder.setAction(slot, key);
            }
        }

        player.openInventory(inv);
    }

    private double calculateStorageValue(Farmer farmer) {
        double total = 0.0;
        FarmerLevel level = plugin.getLevelManager().getLevel(farmer.getLevel());
        for (Map.Entry<String, Long> entry : farmer.getStoredCrops().entrySet()) {
            CropConfig crop = plugin.getCropManager().getCrop(entry.getKey()).orElse(null);
            if (crop != null) {
                total += entry.getValue() * crop.getSellPrice() * level.getMultiplier();
            }
        }
        return total;
    }
}
