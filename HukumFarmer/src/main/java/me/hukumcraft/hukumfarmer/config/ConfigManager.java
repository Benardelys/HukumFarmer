package me.hukumcraft.hukumfarmer.config;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.util.TextUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages configuration and language files for HukumFarmer.
 */
public class ConfigManager {

    private final HukumFarmer plugin;
    private final Map<String, FileConfiguration> configs = new HashMap<>();
    private final Map<String, File> configFiles = new HashMap<>();

    private static final String[] CONFIG_FILES = {
            "config.yml",
            "messages.yml",
            "gui.yml",
            "crops.yml",
            "levels.yml",
            "storage.yml",
            "npc.yml"
    };

    public ConfigManager(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        for (String fileName : CONFIG_FILES) {
            File file = new File(plugin.getDataFolder(), fileName);
            if (!file.exists()) {
                plugin.saveResource(fileName, false);
            }

            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            InputStream defaultStream = plugin.getResource(fileName);
            if (defaultStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                config.setDefaults(defaultConfig);
            }

            configs.put(fileName, config);
            configFiles.put(fileName, file);
        }
    }

    public void reload() {
        configs.clear();
        configFiles.clear();
        loadConfigs();
    }

    public void saveConfig(String fileName) {
        FileConfiguration config = configs.get(fileName);
        File file = configFiles.get(fileName);
        if (config != null && file != null) {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save configuration file: " + fileName, e);
            }
        }
    }

    public FileConfiguration getConfig(String fileName) {
        return configs.getOrDefault(fileName, new YamlConfiguration());
    }

    public FileConfiguration getMainConfig() {
        return getConfig("config.yml");
    }

    public FileConfiguration getMessagesConfig() {
        return getConfig("messages.yml");
    }

    public FileConfiguration getGuiConfig() {
        return getConfig("gui.yml");
    }

    public FileConfiguration getCropsConfig() {
        return getConfig("crops.yml");
    }

    public FileConfiguration getLevelsConfig() {
        return getConfig("levels.yml");
    }

    public FileConfiguration getStorageConfig() {
        return getConfig("storage.yml");
    }

    public FileConfiguration getNpcConfig() {
        return getConfig("npc.yml");
    }

    public void saveNpcConfig() {
        saveConfig("npc.yml");
    }

    public String getPrefix() {
        return getMessagesConfig().getString("prefix", "&6&lHukumFarmer &8» ");
    }

    public String getMessage(String key, String... replacements) {
        String msg = getMessagesConfig().getString(key);
        if (msg == null) {
            return TextUtil.color(getPrefix() + "&cMissing message key: " + key);
        }
        String withPrefix = msg.replace("%prefix%", getPrefix());
        String replaced = TextUtil.replace(withPrefix, replacements);
        return TextUtil.color(replaced);
    }

    public String getMessage(String key) {
        return getMessage(key, new String[0]);
    }
}
