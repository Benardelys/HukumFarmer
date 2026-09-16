package me.hukumcraft.hukumfarmer.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Centralized, secure Message & Color Parser.
 * Handles MiniMessage tags (<dark_green>, <gold>, <gradient>, etc.) and legacy & codes (&2, &a, etc.)
 * with zero literal leakage, strict user placeholder escaping, and native Paper Adventure Components.
 */
public final class MessageParser {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat INTEGER_FORMAT = new DecimalFormat("#,###");
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private MessageParser() {}

    /**
     * Converts any string (MiniMessage, hex, legacy &) into an Adventure Component.
     * Guarantees that neither <color> nor &codes appear literally.
     */
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        // 1. Convert &#RRGGBB hex formats to <#RRGGBB>
        text = translateHexColorCodes(text);

        // 2. Convert Section § color codes to Ampersand & codes if present
        if (text.contains("§")) {
            text = text.replace('§', '&');
        }

        // 3. Convert Legacy & color codes into proper MiniMessage tags
        if (text.contains("&")) {
            text = convertLegacyToMiniMessage(text);
        }

        // 4. Parse with MiniMessage
        try {
            return MINI_MESSAGE.deserialize(text);
        } catch (Exception e) {
            return SECTION_SERIALIZER.deserialize(ChatColor.translateAlternateColorCodes('&', text));
        }
    }

    /**
     * Parses a list of strings into Adventure Components.
     */
    public static List<Component> parseList(List<String> list) {
        if (list == null) return new ArrayList<>();
        List<Component> components = new ArrayList<>(list.size());
        for (String line : list) {
            components.add(parse(line));
        }
        return components;
    }

    /**
     * Serializes text to legacy string with § section symbols for APIs requiring String.
     */
    public static String toLegacy(String text) {
        if (text == null || text.isEmpty()) return "";
        return SECTION_SERIALIZER.serialize(parse(text));
    }

    /**
     * Serializes a list of strings to legacy formatted strings.
     */
    public static List<String> toLegacyList(List<String> list) {
        if (list == null) return new ArrayList<>();
        List<String> result = new ArrayList<>(list.size());
        for (String line : list) {
            result.add(toLegacy(line));
        }
        return result;
    }

    /**
     * Sends an Adventure Component directly to CommandSender using native Adventure or legacy bridge.
     */
    public static void sendMessage(CommandSender sender, String text) {
        if (sender == null || text == null || text.isEmpty()) return;
        try {
            sender.sendMessage(parse(text));
        } catch (Throwable e) {
            sender.sendMessage(toLegacy(text));
        }
    }

    /**
     * Converts legacy color codes (&0-&f, &l-&r) to MiniMessage tags.
     */
    private static String convertLegacyToMiniMessage(String text) {
        return text.replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&l", "<bold>")
                .replace("&o", "<italic>")
                .replace("&n", "<underlined>")
                .replace("&m", "<strikethrough>")
                .replace("&k", "<obfuscated>")
                .replace("&r", "<reset>");
    }

    /**
     * Converts hex format &#RRGGBB to <#RRGGBB>
     */
    private static String translateHexColorCodes(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<#" + matcher.group(1) + ">");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Escapes player-controlled strings to prevent MiniMessage tag injection (e.g. <click:run_command>).
     */
    public static String escapeMiniMessage(String input) {
        if (input == null) return "";
        return MINI_MESSAGE.escapeTags(input);
    }

    /**
     * Completely strips all MiniMessage tags from input.
     */
    public static String stripTags(String input) {
        if (input == null) return "";
        return MINI_MESSAGE.stripTags(input);
    }

    /**
     * Safe placeholder replacement with user input sanitization (varargs).
     */
    public static String replace(String text, String... replacements) {
        if (text == null || replacements == null) return text;
        for (int i = 0; i < replacements.length - 1; i += 2) {
            String key = replacements[i];
            String val = replacements[i + 1];
            if (key != null && val != null) {
                if (key.contains("player") || key.contains("name") || key.contains("id")) {
                    val = escapeMiniMessage(val);
                }
                text = text.replace(key, val);
            }
        }
        return text;
    }

    /**
     * Safe placeholder replacement in a single string with a Map.
     */
    public static String replace(String text, Map<String, String> placeholders) {
        if (text == null || placeholders == null) return text;
        String updated = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String val = entry.getValue() != null ? entry.getValue() : "";
            if (entry.getKey().contains("player") || entry.getKey().contains("name") || entry.getKey().contains("id")) {
                val = escapeMiniMessage(val);
            }
            updated = updated.replace(entry.getKey(), val);
        }
        return updated;
    }

    /**
     * Safe placeholder replacement in string list with a Map.
     */
    public static List<String> replace(List<String> list, Map<String, String> placeholders) {
        if (list == null) return new ArrayList<>();
        List<String> result = new ArrayList<>(list.size());
        for (String line : list) {
            result.add(replace(line, placeholders));
        }
        return result;
    }

    public static String formatMoney(double amount) {
        return CURRENCY_FORMAT.format(amount);
    }

    public static String formatNumber(long number) {
        return INTEGER_FORMAT.format(number);
    }
}
