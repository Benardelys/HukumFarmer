package me.hukumcraft.hukumfarmer.util;

import me.hukumcraft.hukumfarmer.message.MessageParser;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Fluent ItemStack builder for modern Minecraft versions with complete MiniMessage & legacy color support.
 */
public class ItemBuilder {

    private final ItemStack itemStack;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(Material material, int amount) {
        this.itemStack = new ItemStack(material != null ? material : Material.STONE, Math.max(1, amount));
        this.meta = this.itemStack.getItemMeta();
    }

    public ItemBuilder(ItemStack itemStack) {
        this.itemStack = itemStack.clone();
        this.meta = this.itemStack.getItemMeta();
    }

    public static ItemBuilder from(Material material) {
        return new ItemBuilder(material);
    }

    public static ItemBuilder from(ItemStack itemStack) {
        return new ItemBuilder(itemStack);
    }

    public ItemBuilder name(String name) {
        if (meta != null && name != null) {
            meta.displayName(MessageParser.parse(name));
        }
        return this;
    }

    public ItemBuilder name(Component component) {
        if (meta != null && component != null) {
            meta.displayName(component);
        }
        return this;
    }

    public ItemBuilder lore(String... lore) {
        return lore(Arrays.asList(lore));
    }

    public ItemBuilder lore(List<String> lore) {
        if (meta != null && lore != null) {
            meta.lore(MessageParser.parseList(lore));
        }
        return this;
    }

    public ItemBuilder amount(int amount) {
        itemStack.setAmount(Math.max(1, amount));
        return this;
    }

    public ItemBuilder customModelData(int customModelData) {
        if (meta != null && customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        if (meta != null && flags != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    public ItemBuilder hideAllFlags() {
        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
        }
        return this;
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        if (meta != null && enchantment != null) {
            meta.addEnchant(enchantment, level, true);
        }
        return this;
    }

    public ItemBuilder glow() {
        if (meta != null) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (glow) {
            return glow();
        }
        return this;
    }

    public ItemBuilder skullOwner(String ownerName) {
        if (meta instanceof SkullMeta skullMeta && ownerName != null) {
            try {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(ownerName));
            } catch (Exception ignored) {}
        }
        return this;
    }

    public ItemBuilder headTexture(String base64Texture) {
        if (meta instanceof SkullMeta skullMeta && base64Texture != null && !base64Texture.trim().isEmpty()) {
            String clean = base64Texture.trim();
            boolean set = false;
            try {
                com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(java.util.UUID.randomUUID());
                profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", clean));
                skullMeta.setPlayerProfile(profile);
                set = true;
            } catch (Throwable ignored) {}

            if (!set) {
                try {
                    Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
                    Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
                    java.lang.reflect.Constructor<?> gpConstructor = gameProfileClass.getConstructor(java.util.UUID.class, String.class);
                    Object gameProfile = gpConstructor.newInstance(java.util.UUID.randomUUID(), "");
                    java.lang.reflect.Method getProperties = gameProfileClass.getMethod("getProperties");
                    Object propertyMap = getProperties.invoke(gameProfile);
                    java.lang.reflect.Constructor<?> propConstructor = propertyClass.getConstructor(String.class, String.class);
                    Object property = propConstructor.newInstance("textures", clean);
                    java.lang.reflect.Method put = propertyMap.getClass().getMethod("put", Object.class, Object.class);
                    put.invoke(propertyMap, "textures", property);

                    java.lang.reflect.Field profileField = skullMeta.getClass().getDeclaredField("profile");
                    profileField.setAccessible(true);
                    profileField.set(skullMeta, gameProfile);
                } catch (Throwable ignored) {}
            }
        }
        return this;
    }

    public ItemBuilder flags(List<String> flagNames) {
        if (meta != null && flagNames != null) {
            for (String flagName : flagNames) {
                try {
                    ItemFlag flag = ItemFlag.valueOf(flagName.toUpperCase().trim());
                    meta.addItemFlags(flag);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return this;
    }

    public ItemBuilder replacePlaceholders(Map<String, String> placeholders) {
        if (meta != null && placeholders != null) {
            if (meta.hasDisplayName()) {
                String legacyName = MessageParser.toLegacy(meta.getDisplayName());
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    legacyName = legacyName.replace(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
                }
                meta.displayName(MessageParser.parse(legacyName));
            }
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }
}
