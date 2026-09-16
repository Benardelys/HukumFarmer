package me.hukumcraft.hukumfarmer.message;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.language.LanguageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Central Message Manager coordinating multi-language messages and MessageParser.
 */
public class MessageManager {

    private final HukumFarmer plugin;
    private final LanguageManager languageManager;

    public MessageManager(HukumFarmer plugin, LanguageManager languageManager) {
        this.plugin = plugin;
        this.languageManager = languageManager;
    }

    public void sendMessage(CommandSender sender, String key, String... replacements) {
        if (sender == null) return;
        UUID uuid = (sender instanceof Player p) ? p.getUniqueId() : null;
        String rawMessage = languageManager.getMessage(uuid, key, replacements);
        MessageParser.sendMessage(sender, rawMessage);
    }

    public void sendRawMessage(CommandSender sender, String text, String... replacements) {
        if (sender == null || text == null) return;
        String formatted = MessageParser.replace(text, replacements);
        MessageParser.sendMessage(sender, formatted);
    }

    public Component getComponent(UUID uuid, String key, String... replacements) {
        String rawMessage = languageManager.getMessage(uuid, key, replacements);
        return MessageParser.parse(rawMessage);
    }

    public String getRaw(UUID uuid, String key, String... replacements) {
        return languageManager.getMessage(uuid, key, replacements);
    }

    public String getPrefix(UUID uuid) {
        String lang = languageManager.getPlayerLanguage(uuid);
        return languageManager.getPrefix(lang);
    }
}
