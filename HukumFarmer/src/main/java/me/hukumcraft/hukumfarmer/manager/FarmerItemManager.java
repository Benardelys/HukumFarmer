package me.hukumcraft.hukumfarmer.manager;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.compatibility.CompatibilityUtil;
import me.hukumcraft.hukumfarmer.message.MessageParser;
import me.hukumcraft.hukumfarmer.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Centralized Farmer Item Factory and Verification Service.
 * Manages the creation, verification, texture application, and inventory handling of authentic Farmer Eggs.
 * Utilizes PersistentDataContainer (PDC) tokens for counterfeit-proof identification.
 */
public class FarmerItemManager {

    private final HukumFarmer plugin;
    private final NamespacedKey itemTypeKey;
    private final NamespacedKey eggKey;
    private final NamespacedKey tokenKey;

    // Default high-definition Straw Hat Farmer skull texture to prevent Steve heads
    public static final String DEFAULT_FARMER_HEAD_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDY0YzcxMTc5MWFjZWM2ZWVmOGNmMTA0MzM3ZjRlNzVkMGJlNDY1OTVkZWJjNzY4MWRiY2U4YjRhMzQifX19";

    public FarmerItemManager(HukumFarmer plugin) {
        this.plugin = plugin;
        this.itemTypeKey = new NamespacedKey(plugin, "item_type");
        this.eggKey = new NamespacedKey(plugin, "farmer_egg");
        this.tokenKey = new NamespacedKey(plugin, "farmer_egg_token");
    }

    public NamespacedKey getItemTypeKey() {
        return itemTypeKey;
    }

    public NamespacedKey getEggKey() {
        return eggKey;
    }

    public NamespacedKey getTokenKey() {
        return tokenKey;
    }

    /**
     * Creates a new authentic Farmer Egg ItemStack with persistent data markings,
     * configured textures, CustomModelData, glow, flags, and display meta.
     *
     * @param amount The number of eggs in the stack.
     * @return Fully configured authentic Farmer Egg ItemStack.
     */
    public ItemStack createFarmerEgg(int amount) {
        FileConfiguration config = plugin.getConfigManager().getMainConfig();
        int safeAmount = Math.max(1, Math.min(64, amount));

        ItemStack item = null;

        // 1. Try ItemsAdder custom item if enabled
        if (config.getBoolean("farmer-item.itemsadder.enabled", false)) {
            String iaId = config.getString("farmer-item.itemsadder.item-id", "hukumfarmer:farmer_egg");
            if (plugin.getItemsAdderHook().isAvailable()) {
                ItemStack iaStack = plugin.getItemsAdderHook().getItem(iaId);
                if (iaStack != null) {
                    iaStack.setAmount(safeAmount);
                    item = iaStack;
                } else {
                    plugin.getLogger().warning("[HukumFarmer] ItemsAdder custom item '" + iaId + "' not found! Falling back to configured Bukkit material.");
                }
            } else {
                plugin.getLogger().warning("[HukumFarmer] ItemsAdder integration is enabled in config, but ItemsAdder plugin is not active! Falling back to configured Bukkit material.");
            }
        }

        // 2. Standard Bukkit/Paper Item Construction
        if (item == null) {
            String matName = config.getString("farmer-item.material", "VILLAGER_SPAWN_EGG");
            Optional<Material> optMat = CompatibilityUtil.matchMaterial(matName);
            Material material;

            if (optMat.isPresent()) {
                material = optMat.get();
            } else {
                plugin.getLogger().log(Level.WARNING, "[HukumFarmer] Invalid material '" + matName + "' configured for farmer-item! Falling back to VILLAGER_SPAWN_EGG.");
                material = Material.VILLAGER_SPAWN_EGG;
            }

            String rawName = config.getString("farmer-item.name", "<green><bold>🌾 Çiftçi Yumurtası</bold></green>");
            List<String> rawLore = config.getStringList("farmer-item.lore");
            if (rawLore.isEmpty()) {
                rawLore = Arrays.asList(
                        "<gray>Yere sağ tıklayarak kendi çiftçinizi</gray>",
                        "<gray>bulunduğunuz konuma yerleştirin.</gray>",
                        "",
                        "<yellow>⚡ Otomatik Hasat ve Depolama</yellow>",
                        "<yellow>⚡ Seviye ve Geliştirmeler</yellow>",
                        "",
                        "<green>Sağ tıklayarak yerleştirin!</green>"
                );
            }

            boolean glow = config.getBoolean("farmer-item.glow", true);
            int customModelData = config.getInt("farmer-item.custom-model-data", 0);
            List<String> flags = config.getStringList("farmer-item.flags");

            ItemBuilder builder = ItemBuilder.from(material)
                    .amount(safeAmount)
                    .name(rawName)
                    .lore(rawLore)
                    .customModelData(customModelData)
                    .flags(flags)
                    .glow(glow);

            // Handle Head Textures if PLAYER_HEAD is configured
            if (material == Material.PLAYER_HEAD) {
                String customTexture = config.getString("farmer-item.head.texture");
                String headOwner = config.getString("farmer-item.head.owner");

                if (customTexture != null && !customTexture.trim().isEmpty()) {
                    builder.headTexture(customTexture.trim());
                } else if (headOwner != null && !headOwner.trim().isEmpty()) {
                    builder.skullOwner(headOwner.trim());
                } else {
                    // Apply pre-configured farmer head skin so it NEVER appears as a default Steve head
                    builder.headTexture(DEFAULT_FARMER_HEAD_TEXTURE);
                }
            }

            item = builder.build();
        }

        // 3. Always apply tamper-proof PDC identification tokens
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(itemTypeKey, PersistentDataType.STRING, "farmer_egg");
            meta.getPersistentDataContainer().set(eggKey, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(tokenKey, PersistentDataType.STRING, "hukumfarmer_egg_" + UUID.randomUUID());
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Checks whether an ItemStack is a valid, authentic HukumFarmer Farmer Egg via secure PDC inspection.
     * Tampering with display name, lore, material, or CustomModelData will never bypass or fool this check.
     */
    public boolean isFarmerEgg(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        // Check primary item_type key
        if (meta.getPersistentDataContainer().has(itemTypeKey, PersistentDataType.STRING)) {
            String type = meta.getPersistentDataContainer().get(itemTypeKey, PersistentDataType.STRING);
            if ("farmer_egg".equalsIgnoreCase(type)) {
                return true;
            }
        }

        // Check legacy/backup byte key
        return meta.getPersistentDataContainer().has(eggKey, PersistentDataType.BYTE);
    }

    /**
     * Verifies if the player has adequate inventory space to receive the given item.
     */
    public boolean hasInventorySpace(Player player, ItemStack itemToAdd) {
        if (player == null || itemToAdd == null) return false;

        boolean stackable = plugin.getConfigManager().getMainConfig().getBoolean("farmer-item.stackable", false);
        int needed = itemToAdd.getAmount();

        ItemStack[] contents = player.getInventory().getStorageContents();
        for (ItemStack slotItem : contents) {
            if (slotItem == null || slotItem.getType() == Material.AIR) {
                return true; // Found empty slot
            }
            if (stackable && slotItem.isSimilar(itemToAdd)) {
                int maxStack = slotItem.getMaxStackSize();
                int space = maxStack - slotItem.getAmount();
                if (space >= needed) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isPreventCreativeDuplication() {
        return plugin.getConfigManager().getMainConfig().getBoolean("farmer-item.prevent-creative-duplication", true);
    }

    public boolean isDropOnDeath() {
        return plugin.getConfigManager().getMainConfig().getBoolean("farmer-item.drop-on-death", true);
    }
}
