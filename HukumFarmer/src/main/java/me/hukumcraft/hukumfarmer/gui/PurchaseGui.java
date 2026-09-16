package me.hukumcraft.hukumfarmer.gui;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.compatibility.CompatibilityUtil;
import me.hukumcraft.hukumfarmer.message.MessageParser;
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
 * Farmer Purchase GUI allowing players to review pricing and confirm purchase.
 */
public class PurchaseGui {

    private final HukumFarmer plugin;

    public PurchaseGui(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        FileConfiguration guiConfig = plugin.getConfigManager().getGuiConfig();
        ConfigurationSection menuSec = guiConfig.getConfigurationSection("purchase-menu");
        if (menuSec == null) return;

        int size = menuSec.getInt("size", 36);
        String defaultTitle = menuSec.getString("title", "<gold><bold>HukumFarmer - Çiftçi Satın Al</bold></gold>");
        String title = plugin.getLanguageManager().getGuiTitle(player.getUniqueId(), "purchase-menu", defaultTitle);

        FarmerGuiHolder holder = new FarmerGuiHolder(null, GuiType.PURCHASE);
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

        double price = plugin.getConfigManager().getMainConfig().getDouble("farmer.purchase.price", 50000.0);
        double balance = plugin.getVaultHook().isHooked() ? plugin.getVaultHook().getBalance(player) : 0.0;
        String lang = plugin.getLanguageManager().getPlayerLanguage(player.getUniqueId());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%price%", TextUtil.formatMoney(price));
        placeholders.put("%balance%", TextUtil.formatMoney(balance));
        placeholders.put("%player%", player.getName());
        placeholders.put("%language%", lang.toUpperCase());

        // Info Item
        ConfigurationSection infoSec = menuSec.getConfigurationSection("info-item");
        if (infoSec != null) {
            int slot = infoSec.getInt("slot", 13);
            String matName = infoSec.getString("material", "PLAYER_HEAD");
            Material mat = CompatibilityUtil.matchMaterial(matName).orElse(Material.PLAYER_HEAD);

            String rawName = infoSec.getString("name", "<yellow><bold>🌾 HukumFarmer Çiftçisi</bold></yellow>");
            List<String> rawLore = infoSec.getStringList("lore");

            String locName = plugin.getLanguageManager().getGuiItemName(player.getUniqueId(), "purchase-menu", "info-item", rawName);
            List<String> locLore = plugin.getLanguageManager().getGuiItemLore(player.getUniqueId(), "purchase-menu", "info-item", rawLore);

            String name = MessageParser.replace(locName, placeholders);
            List<String> lore = MessageParser.replace(locLore, placeholders);

            ItemBuilder builder = ItemBuilder.from(mat)
                    .name(name)
                    .lore(lore)
                    .customModelData(infoSec.getInt("custom-model-data", 0));

            if (mat == Material.PLAYER_HEAD) {
                builder.skullOwner(player.getName());
            }

            inv.setItem(slot, builder.build());
        }

        // Confirm Button
        ConfigurationSection confirmSec = menuSec.getConfigurationSection("confirm-button");
        if (confirmSec != null) {
            int slot = confirmSec.getInt("slot", 20);
            String matName = confirmSec.getString("material", "EMERALD_BLOCK");
            Material mat = CompatibilityUtil.matchMaterial(matName).orElse(Material.EMERALD_BLOCK);

            String rawName = confirmSec.getString("name", "<green><bold>✔ SATIN AL VE OLUŞTUR</bold></green>");
            List<String> rawLore = confirmSec.getStringList("lore");

            String locName = plugin.getLanguageManager().getGuiItemName(player.getUniqueId(), "purchase-menu", "confirm-button", rawName);
            List<String> locLore = plugin.getLanguageManager().getGuiItemLore(player.getUniqueId(), "purchase-menu", "confirm-button", rawLore);

            String name = MessageParser.replace(locName, placeholders);
            List<String> lore = MessageParser.replace(locLore, placeholders);

            ItemStack confirmItem = ItemBuilder.from(mat)
                    .name(name)
                    .lore(lore)
                    .customModelData(confirmSec.getInt("custom-model-data", 0))
                    .build();

            inv.setItem(slot, confirmItem);
            holder.setAction(slot, "CONFIRM_PURCHASE");
        }

        // Cancel Button
        ConfigurationSection cancelSec = menuSec.getConfigurationSection("cancel-button");
        if (cancelSec != null) {
            int slot = cancelSec.getInt("slot", 24);
            String matName = cancelSec.getString("material", "REDSTONE_BLOCK");
            Material mat = CompatibilityUtil.matchMaterial(matName).orElse(Material.REDSTONE_BLOCK);

            String rawName = cancelSec.getString("name", "<red><bold>✖ İPTAL ET</bold></red>");
            List<String> rawLore = cancelSec.getStringList("lore");

            String locName = plugin.getLanguageManager().getGuiItemName(player.getUniqueId(), "purchase-menu", "cancel-button", rawName);
            List<String> locLore = plugin.getLanguageManager().getGuiItemLore(player.getUniqueId(), "purchase-menu", "cancel-button", rawLore);

            String name = MessageParser.replace(locName, placeholders);
            List<String> lore = MessageParser.replace(locLore, placeholders);

            ItemStack cancelItem = ItemBuilder.from(mat)
                    .name(name)
                    .lore(lore)
                    .customModelData(cancelSec.getInt("custom-model-data", 0))
                    .build();

            inv.setItem(slot, cancelItem);
            holder.setAction(slot, "CANCEL");
        }

        // Language Button
        ConfigurationSection langSec = menuSec.getConfigurationSection("language-button");
        if (langSec != null) {
            int slot = langSec.getInt("slot", 31);
            String matName = langSec.getString("material", "BOOK");
            Material mat = CompatibilityUtil.matchMaterial(matName).orElse(Material.BOOK);

            String rawName = langSec.getString("name", "<aqua><bold>🌐 Dil Değiştir</bold></aqua>");
            List<String> rawLore = langSec.getStringList("lore");

            String locName = plugin.getLanguageManager().getGuiItemName(player.getUniqueId(), "purchase-menu", "language-button", rawName);
            List<String> locLore = plugin.getLanguageManager().getGuiItemLore(player.getUniqueId(), "purchase-menu", "language-button", rawLore);

            ItemStack langItem = ItemBuilder.from(mat)
                    .name(locName)
                    .lore(locLore)
                    .customModelData(langSec.getInt("custom-model-data", 0))
                    .build();

            inv.setItem(slot, langItem);
            holder.setAction(slot, "OPEN_LANGUAGE");
        }

        player.openInventory(inv);
    }
}
