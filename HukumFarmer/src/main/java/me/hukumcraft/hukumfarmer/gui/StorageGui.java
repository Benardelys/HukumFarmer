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

import java.util.*;

/**
 * Storage GUI displaying crop stocks, selling actions, and withdrawals.
 */
public class StorageGui {

    private final HukumFarmer plugin;

    public StorageGui(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Farmer farmer) {
        FileConfiguration guiConfig = plugin.getConfigManager().getGuiConfig();
        FileConfiguration storageConfig = plugin.getConfigManager().getStorageConfig();
        ConfigurationSection menuSec = guiConfig.getConfigurationSection("storage-menu");
        if (menuSec == null) return;

        int size = menuSec.getInt("size", 54);
        String defaultTitle = menuSec.getString("title", "<gold><bold>Çiftçi Ürün Deposu</bold></gold>");
        String title = plugin.getLanguageManager().getGuiTitle(player.getUniqueId(), "storage-menu", defaultTitle);

        FarmerGuiHolder holder = new FarmerGuiHolder(farmer, GuiType.STORAGE);
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

        FarmerLevel level = plugin.getLevelManager().getLevel(farmer.getLevel());
        long totalStoredItems = farmer.getTotalStoredItems();
        double totalSellValue = 0.0;

        List<String> rawCropLore = storageConfig.getStringList("crop-item-format.lore");
        if (rawCropLore.isEmpty()) {
            rawCropLore = Arrays.asList(
                    "<gray>Depolanan Miktar: <yellow>%amount%</yellow></gray>",
                    "<gray>Birim Fiyatı: <gold>%price%$</gold></gray>",
                    "<gray>Toplam Değer: <green>%total_value%$</green></gray>",
                    "",
                    "<yellow>▶ [Sol Tık] Bu üründen 64 adet sat</yellow>",
                    "<gold>▶ [Sağ Tık] Bu ürünün HEPSİNİ sat</gold>",
                    "<aqua>▶ [Shift+Sol Tık] 64 adet envantere al</aqua>"
            );
        }
        List<String> cropLoreTemplate = plugin.getLanguageManager().getStorageCropLore(player.getUniqueId(), rawCropLore);

        // Populate crop items
        for (CropConfig crop : plugin.getCropManager().getAllCrops()) {
            int slot = crop.getGuiSlot();
            if (slot < 0 || slot >= size) continue;

            long amount = farmer.getCropAmount(crop.getId());
            double cropValue = amount * crop.getSellPrice() * level.getMultiplier();
            totalSellValue += cropValue;

            String cropDisplayName = plugin.getLanguageManager().getCropDisplayName(player.getUniqueId(), crop.getId(), crop.getDisplayName());

            Map<String, String> cropPlaceholders = new HashMap<>();
            cropPlaceholders.put("%amount%", TextUtil.formatNumber(amount));
            cropPlaceholders.put("%price%", TextUtil.formatMoney(crop.getSellPrice() * level.getMultiplier()));
            cropPlaceholders.put("%total_value%", TextUtil.formatMoney(cropValue));
            cropPlaceholders.put("%crop_name%", cropDisplayName);

            List<String> formattedLore = MessageParser.replace(cropLoreTemplate, cropPlaceholders);

            ItemStack cropItem = ItemBuilder.from(crop.getItemMaterial())
                    .name(cropDisplayName)
                    .lore(formattedLore)
                    .amount(amount > 0 ? (int) Math.min(64, Math.max(1, amount)) : 1)
                    .build();

            inv.setItem(slot, cropItem);
            holder.setAction(slot, "CROP:" + crop.getId());
        }

        // Sell All Button
        ConfigurationSection sellAllSec = menuSec.getConfigurationSection("sell-all-button");
        if (sellAllSec != null) {
            int slot = sellAllSec.getInt("slot", 49);
            String matName = sellAllSec.getString("material", "SUNFLOWER");
            Material mat = CompatibilityUtil.matchMaterial(matName).orElse(Material.SUNFLOWER);

            Map<String, String> sellPlaceholders = new HashMap<>();
            sellPlaceholders.put("%total_stored_items%", TextUtil.formatNumber(totalStoredItems));
            sellPlaceholders.put("%total_sell_value%", TextUtil.formatMoney(totalSellValue));

            String rawName = sellAllSec.getString("name", "<green><bold>Tüm Ürünleri Sat</bold></green>");
            List<String> rawLore = sellAllSec.getStringList("lore");

            String locName = plugin.getLanguageManager().getGuiItemName(player.getUniqueId(), "storage-menu", "sell-all-button", rawName);
            List<String> locLore = plugin.getLanguageManager().getGuiItemLore(player.getUniqueId(), "storage-menu", "sell-all-button", rawLore);

            String name = MessageParser.replace(locName, sellPlaceholders);
            List<String> lore = MessageParser.replace(locLore, sellPlaceholders);

            ItemStack sellAllItem = ItemBuilder.from(mat)
                    .name(name)
                    .lore(lore)
                    .build();

            inv.setItem(slot, sellAllItem);
            holder.setAction(slot, "SELL_ALL");
        }

        // Back Button
        ConfigurationSection backSec = menuSec.getConfigurationSection("back-button");
        if (backSec != null) {
            int slot = backSec.getInt("slot", 45);
            String matName = backSec.getString("material", "ARROW");
            Material mat = CompatibilityUtil.matchMaterial(matName).orElse(Material.ARROW);

            String rawName = backSec.getString("name", "<red><bold>Geri Dön</bold></red>");
            List<String> rawLore = backSec.getStringList("lore");

            String locName = plugin.getLanguageManager().getGuiItemName(player.getUniqueId(), "storage-menu", "back-button", rawName);
            List<String> locLore = plugin.getLanguageManager().getGuiItemLore(player.getUniqueId(), "storage-menu", "back-button", rawLore);

            ItemStack backItem = ItemBuilder.from(mat)
                    .name(locName)
                    .lore(locLore)
                    .build();

            inv.setItem(slot, backItem);
            holder.setAction(slot, "BACK");
        }

        // Info Item
        ConfigurationSection infoSec = menuSec.getConfigurationSection("info-item");
        if (infoSec != null) {
            int slot = infoSec.getInt("slot", 53);
            String matName = infoSec.getString("material", "CHEST");
            Material mat = CompatibilityUtil.matchMaterial(matName).orElse(Material.CHEST);

            Map<String, String> infoPlaceholders = new HashMap<>();
            infoPlaceholders.put("%current_storage%", TextUtil.formatNumber(totalStoredItems));
            infoPlaceholders.put("%max_storage%", TextUtil.formatNumber(level.getMaxStorage()));
            infoPlaceholders.put("%multiplier%", String.format("%.2f", level.getMultiplier()));

            String rawName = infoSec.getString("name", "<yellow><bold>Depo Bilgisi</bold></yellow>");
            List<String> rawLore = infoSec.getStringList("lore");

            String locName = plugin.getLanguageManager().getGuiItemName(player.getUniqueId(), "storage-menu", "info-item", rawName);
            List<String> locLore = plugin.getLanguageManager().getGuiItemLore(player.getUniqueId(), "storage-menu", "info-item", rawLore);

            String name = MessageParser.replace(locName, infoPlaceholders);
            List<String> lore = MessageParser.replace(locLore, infoPlaceholders);

            ItemStack infoItem = ItemBuilder.from(mat)
                    .name(name)
                    .lore(lore)
                    .build();

            inv.setItem(slot, infoItem);
        }

        player.openInventory(inv);
    }
}
