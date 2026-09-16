package me.hukumcraft.hukumfarmer.language;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.message.MessageParser;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Robust multi-language engine supporting 6 languages (tr, en, de, fr, es, ru)
 * with dynamic availability checks, fallback resolution, and safe placeholder replacement.
 */
public class LanguageManager {

    private final HukumFarmer plugin;
    private final Map<String, FileConfiguration> languageConfigs = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerLanguages = new ConcurrentHashMap<>();

    private static final List<String> ALL_LANGUAGES = Arrays.asList("tr", "en", "de", "fr", "es", "ru");
    private List<String> availableLanguages = new ArrayList<>(ALL_LANGUAGES);
    private String defaultLanguage = "tr";

    public LanguageManager(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    public void loadLanguages() {
        languageConfigs.clear();
        defaultLanguage = plugin.getConfigManager().getMainConfig().getString("language.default", "en").toLowerCase();

        List<String> cfgAvailable = plugin.getConfigManager().getMainConfig().getStringList("language.available");
        if (!cfgAvailable.isEmpty()) {
            availableLanguages = new ArrayList<>();
            for (String code : cfgAvailable) {
                if (ALL_LANGUAGES.contains(code.toLowerCase())) {
                    availableLanguages.add(code.toLowerCase());
                }
            }
        } else {
            availableLanguages = new ArrayList<>(ALL_LANGUAGES);
        }

        if (!availableLanguages.contains(defaultLanguage)) {
            defaultLanguage = availableLanguages.isEmpty() ? "en" : availableLanguages.get(0);
        }

        File langFolder = new File(plugin.getDataFolder(), "languages");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        for (String langCode : ALL_LANGUAGES) {
            String fileName = "languages/" + langCode + ".yml";
            File file = new File(plugin.getDataFolder(), fileName);

            if (!file.exists()) {
                plugin.saveResource(fileName, false);
            }

            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                InputStream defaultStream = plugin.getResource(fileName);
                if (defaultStream != null) {
                    YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
                    );
                    config.setDefaults(defaultConfig);
                }
                languageConfigs.put(langCode, config);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load language file: " + fileName, e);
            }
        }

        plugin.getLogger().info("Successfully loaded " + languageConfigs.size() + " languages. Enabled: " + availableLanguages);
    }

    public String getPlayerLanguage(UUID uuid) {
        if (uuid == null) return defaultLanguage;
        String lang = playerLanguages.get(uuid);
        if (lang != null && availableLanguages.contains(lang)) {
            return lang;
        }
        return defaultLanguage;
    }

    public void setPlayerLanguage(UUID uuid, String langCode) {
        if (uuid == null || langCode == null) return;
        String clean = langCode.toLowerCase().trim();
        if (!availableLanguages.contains(clean)) {
            clean = defaultLanguage;
        }
        playerLanguages.put(uuid, clean);
    }

    public boolean isSupportedLanguage(String langCode) {
        if (langCode == null) return false;
        return availableLanguages.contains(langCode.toLowerCase().trim());
    }

    public List<String> getAvailableLanguages() {
        return Collections.unmodifiableList(availableLanguages);
    }

    /**
     * Resolves raw message string with safe 3-layer fallback:
     * 1. Player's selected language
     * 2. Default language / English
     * 3. Hardcoded safe string
     */
    public String getRawMessage(UUID uuid, String key, String... replacements) {
        String lang = getPlayerLanguage(uuid);
        String prefix = getPrefix(lang);

        FileConfiguration userConfig = languageConfigs.get(lang);
        String msg = null;

        if (userConfig != null) {
            msg = userConfig.getString(key);
        }

        // Fallback 1: English (en)
        if (msg == null) {
            FileConfiguration enConfig = languageConfigs.get("en");
            if (enConfig != null) {
                msg = enConfig.getString(key);
            }
        }

        // Fallback 2: Default language (tr)
        if (msg == null) {
            FileConfiguration defConfig = languageConfigs.get(defaultLanguage);
            if (defConfig != null) {
                msg = defConfig.getString(key);
            }
        }

        // Fallback 3: Hardcoded safe
        if (msg == null) {
            if (plugin.getConfigManager().getMainConfig().getBoolean("logging.debug", false)) {
                plugin.getLogger().warning("Missing translation key: '" + key + "' across all language fallbacks.");
            }
            return prefix + "&c[Missing translation: " + key + "]";
        }

        String withPrefix = msg.replace("%prefix%", prefix);
        return MessageParser.replace(withPrefix, replacements);
    }

    /**
     * Resolves a fully formatted legacy colored string (with §) so that any Bukkit player.sendMessage(String)
     * call correctly displays full colors without leaking MiniMessage tags like <gold> or <green>.
     */
    public String getMessage(UUID uuid, String key, String... replacements) {
        return MessageParser.toLegacy(getRawMessage(uuid, key, replacements));
    }

    public String getMessage(Player player, String key, String... replacements) {
        UUID uuid = player != null ? player.getUniqueId() : null;
        return getMessage(uuid, key, replacements);
    }

    /**
     * Resolves an Adventure Component for native Paper Adventure messaging & GUI titles.
     */
    public net.kyori.adventure.text.Component getComponent(UUID uuid, String key, String... replacements) {
        return MessageParser.parse(getRawMessage(uuid, key, replacements));
    }

    public net.kyori.adventure.text.Component getComponent(Player player, String key, String... replacements) {
        UUID uuid = player != null ? player.getUniqueId() : null;
        return getComponent(uuid, key, replacements);
    }

    /**
     * Directly sends a parsed message to any CommandSender using Adventure Components.
     */
    public void sendMessage(org.bukkit.command.CommandSender sender, String key, String... replacements) {
        if (sender == null) return;
        UUID uuid = (sender instanceof Player p) ? p.getUniqueId() : null;
        MessageParser.sendMessage(sender, getRawMessage(uuid, key, replacements));
    }

    public List<String> getMessageList(UUID uuid, String key) {
        String lang = getPlayerLanguage(uuid);
        String prefix = getPrefix(lang);

        FileConfiguration userConfig = languageConfigs.get(lang);
        List<String> list = null;

        if (userConfig != null) {
            list = userConfig.getStringList(key);
        }

        if ((list == null || list.isEmpty())) {
            FileConfiguration enConfig = languageConfigs.get("en");
            if (enConfig != null) {
                list = enConfig.getStringList(key);
            }
        }

        if ((list == null || list.isEmpty())) {
            FileConfiguration defConfig = languageConfigs.get(defaultLanguage);
            if (defConfig != null) {
                list = defConfig.getStringList(key);
            }
        }

        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> formatted = new ArrayList<>(list.size());
        for (String line : list) {
            formatted.add(MessageParser.toLegacy(line.replace("%prefix%", prefix)));
        }
        return formatted;
    }

    public List<String> getMessageList(Player player, String key) {
        UUID uuid = player != null ? player.getUniqueId() : null;
        return getMessageList(uuid, key);
    }

    public List<net.kyori.adventure.text.Component> getComponentList(UUID uuid, String key) {
        List<String> list = getMessageList(uuid, key);
        return MessageParser.parseList(list);
    }

    public List<net.kyori.adventure.text.Component> getComponentList(Player player, String key) {
        UUID uuid = player != null ? player.getUniqueId() : null;
        return getComponentList(uuid, key);
    }

    public String getPrefix(String lang) {
        FileConfiguration config = languageConfigs.get(lang);
        if (config != null) {
            String p = config.getString("prefix");
            if (p != null) return p;
        }
        return plugin.getConfigManager().getMainConfig().getString("prefix", "<gold><bold>HukumFarmer</bold></gold> <dark_gray>»</dark_gray> ");
    }

    /**
     * Resolves a localized GUI title with multi-layer fallback.
     */
    public String getGuiTitle(UUID uuid, String menuKey, String defaultTitle) {
        String key = "gui." + menuKey + ".title";
        String lang = getPlayerLanguage(uuid);

        FileConfiguration userConfig = languageConfigs.get(lang);
        if (userConfig != null && userConfig.contains(key)) {
            return userConfig.getString(key);
        }

        FileConfiguration enConfig = languageConfigs.get("en");
        if (enConfig != null && enConfig.contains(key)) {
            return enConfig.getString(key);
        }

        FileConfiguration defConfig = languageConfigs.get(defaultLanguage);
        if (defConfig != null && defConfig.contains(key)) {
            return defConfig.getString(key);
        }

        return defaultTitle;
    }

    /**
     * Resolves a localized GUI item display name.
     */
    public String getGuiItemName(UUID uuid, String menuKey, String itemKey, String defaultName) {
        String key = "gui." + menuKey + "." + itemKey + ".name";
        String lang = getPlayerLanguage(uuid);

        FileConfiguration userConfig = languageConfigs.get(lang);
        if (userConfig != null && userConfig.contains(key)) {
            return userConfig.getString(key);
        }

        FileConfiguration enConfig = languageConfigs.get("en");
        if (enConfig != null && enConfig.contains(key)) {
            return enConfig.getString(key);
        }

        FileConfiguration defConfig = languageConfigs.get(defaultLanguage);
        if (defConfig != null && defConfig.contains(key)) {
            return defConfig.getString(key);
        }

        return defaultName;
    }

    /**
     * Resolves a localized GUI item lore list.
     */
    public List<String> getGuiItemLore(UUID uuid, String menuKey, String itemKey, List<String> defaultLore) {
        String key = "gui." + menuKey + "." + itemKey + ".lore";
        String lang = getPlayerLanguage(uuid);

        FileConfiguration userConfig = languageConfigs.get(lang);
        if (userConfig != null && userConfig.contains(key)) {
            return userConfig.getStringList(key);
        }

        FileConfiguration enConfig = languageConfigs.get("en");
        if (enConfig != null && enConfig.contains(key)) {
            return enConfig.getStringList(key);
        }

        FileConfiguration defConfig = languageConfigs.get(defaultLanguage);
        if (defConfig != null && defConfig.contains(key)) {
            return defConfig.getStringList(key);
        }

        return defaultLore;
    }

    /**
     * Resolves a localized crop lore list format in storage menu.
     */
    public List<String> getStorageCropLore(UUID uuid, List<String> defaultLore) {
        String key = "gui.storage-menu.crop-lore";
        String lang = getPlayerLanguage(uuid);

        FileConfiguration userConfig = languageConfigs.get(lang);
        if (userConfig != null && userConfig.contains(key)) {
            return userConfig.getStringList(key);
        }

        FileConfiguration enConfig = languageConfigs.get("en");
        if (enConfig != null && enConfig.contains(key)) {
            return enConfig.getStringList(key);
        }

        FileConfiguration defConfig = languageConfigs.get(defaultLanguage);
        if (defConfig != null && defConfig.contains(key)) {
            return defConfig.getStringList(key);
        }

        return defaultLore;
    }

    /**
     * Resolves localized crop display name (e.g. Wheat, Karotte, etc.).
     */
    public String getCropDisplayName(UUID uuid, String cropId, String defaultDisplayName) {
        if (cropId == null) return defaultDisplayName;
        String key = "crops." + cropId.toLowerCase();
        String lang = getPlayerLanguage(uuid);

        FileConfiguration userConfig = languageConfigs.get(lang);
        if (userConfig != null && userConfig.contains(key)) {
            return userConfig.getString(key);
        }

        FileConfiguration enConfig = languageConfigs.get("en");
        if (enConfig != null && enConfig.contains(key)) {
            return enConfig.getString(key);
        }

        FileConfiguration defConfig = languageConfigs.get(defaultLanguage);
        if (defConfig != null && defConfig.contains(key)) {
            return defConfig.getString(key);
        }

        return defaultDisplayName;
    }

    /**
     * Returns the localized status string for enabled/disabled.
     */
    public String getStatusString(UUID uuid, boolean enabled) {
        return getRawMessage(uuid, enabled ? "status-enabled" : "status-disabled");
    }
}
