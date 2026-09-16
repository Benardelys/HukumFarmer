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

import java.util.List;

/**
 * Language Selection GUI for switching player localization in real-time.
 */
public class LanguageGui {

    private final HukumFarmer plugin;

    public LanguageGui(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Farmer farmer) {
        FileConfiguration guiConfig = plugin.getConfigManager().getGuiConfig();
        ConfigurationSection menuSec = guiConfig.getConfigurationSection("language-menu");
        if (menuSec == null) return;

        int size = menuSec.getInt("size", 27);
        String defaultTitle = menuSec.getString("title", "<aqua><bold>🌐 Dil Seçimi / Language Selection</bold></aqua>");
        String title = plugin.getLanguageManager().getGuiTitle(player.getUniqueId(), "language-menu", defaultTitle);

        FarmerGuiHolder holder = new FarmerGuiHolder(farmer, GuiType.LANGUAGE);
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

        String currentLang = plugin.getLanguageManager().getPlayerLanguage(player.getUniqueId());
        List<String> available = plugin.getLanguageManager().getAvailableLanguages();

        ConfigurationSection langSec = menuSec.getConfigurationSection("languages");
        if (langSec != null) {
            for (String code : langSec.getKeys(false)) {
                // If administrator disabled this language, do not display it
                if (!available.contains(code.toLowerCase())) {
                    continue;
                }

                ConfigurationSection itemSec = langSec.getConfigurationSection(code);
                if (itemSec == null) continue;

                int slot = itemSec.getInt("slot", 0);
                String matName = itemSec.getString("material", "BOOK");
                Material mat = CompatibilityUtil.matchMaterial(matName).orElse(Material.BOOK);

                String name = itemSec.getString("name", code.toUpperCase());
                List<String> lore = itemSec.getStringList("lore");
                int cmd = itemSec.getInt("custom-model-data", 0);

                ItemBuilder builder = ItemBuilder.from(mat)
                        .name(name)
                        .lore(lore)
                        .customModelData(cmd);

                if (code.equalsIgnoreCase(currentLang)) {
                    builder.glow();
                }

                inv.setItem(slot, builder.build());
                holder.setAction(slot, "LANG:" + code.toLowerCase());
            }
        }

        // Back Button
        ConfigurationSection backSec = menuSec.getConfigurationSection("back-button");
        if (backSec != null) {
            int slot = backSec.getInt("slot", 22);
            String matName = backSec.getString("material", "ARROW");
            Material mat = CompatibilityUtil.matchMaterial(matName).orElse(Material.ARROW);

            String rawName = backSec.getString("name", "<red><bold>Geri Dön / Back</bold></red>");
            List<String> rawLore = backSec.getStringList("lore");

            String locName = plugin.getLanguageManager().getGuiItemName(player.getUniqueId(), "language-menu", "back-button", rawName);
            List<String> locLore = plugin.getLanguageManager().getGuiItemLore(player.getUniqueId(), "language-menu", "back-button", rawLore);

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
