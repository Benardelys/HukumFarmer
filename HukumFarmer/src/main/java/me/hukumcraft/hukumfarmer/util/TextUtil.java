package me.hukumcraft.hukumfarmer.util;

import me.hukumcraft.hukumfarmer.message.MessageParser;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

/**
 * Legacy compatibility wrapper delegating directly to {@link MessageParser}.
 */
public final class TextUtil {

    private TextUtil() {}

    public static Component parse(String text) {
        return MessageParser.parse(text);
    }

    public static List<Component> parseList(List<String> list) {
        return MessageParser.parseList(list);
    }

    public static String color(String text) {
        return MessageParser.toLegacy(text);
    }

    public static List<String> color(List<String> list) {
        return MessageParser.toLegacyList(list);
    }

    public static void sendMessage(CommandSender sender, String text) {
        MessageParser.sendMessage(sender, text);
    }

    public static String replace(String text, String... replacements) {
        return MessageParser.replace(text, replacements);
    }

    public static List<String> replace(List<String> list, Map<String, String> placeholders) {
        return MessageParser.replace(list, placeholders);
    }

    public static String escapeMiniMessage(String input) {
        return MessageParser.escapeMiniMessage(input);
    }

    public static String formatMoney(double amount) {
        return MessageParser.formatMoney(amount);
    }

    public static String formatNumber(long number) {
        return MessageParser.formatNumber(number);
    }

    public static String formatCompact(long number) {
        if (number < 1000) return String.valueOf(number);
        if (number < 1_000_000) return String.format("%.1fK", number / 1000.0);
        if (number < 1_000_000_000) return String.format("%.1fM", number / 1_000_000.0);
        return String.format("%.1fB", number / 1_000_000_000.0);
    }
}
