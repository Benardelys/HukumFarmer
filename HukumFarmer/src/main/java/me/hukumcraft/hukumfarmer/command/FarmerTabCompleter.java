package me.hukumcraft.hukumfarmer.command;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.model.FarmerNPC;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Intelligent tab-completion for HukumFarmer commands.
 */
public class FarmerTabCompleter implements TabCompleter {

    private static final List<String> MAIN_SUBS = Arrays.asList(
            "help", "create", "menu", "language", "storage", "upgrade", "sell", "stats", "settings", "reload", "admin", "npc"
    );

    private static final List<String> ADMIN_SUBS = Arrays.asList(
            "give", "language", "reset", "setlevel", "addxp", "remove", "info"
    );

    private static final List<String> NPC_SUBS = Arrays.asList(
            "create", "remove", "list", "teleport"
    );

    private static final List<String> LANGUAGES = Arrays.asList(
            "tr", "en", "de", "fr", "es", "ru"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> allowed = new ArrayList<>();
            for (String sub : MAIN_SUBS) {
                if ("admin".equals(sub) && !sender.hasPermission("hukumfarmer.admin")) continue;
                if ("npc".equals(sub) && !sender.hasPermission("hukumfarmer.npc")) continue;
                if ("reload".equals(sub) && !sender.hasPermission("hukumfarmer.reload")) continue;
                allowed.add(sub);
            }
            StringUtil.copyPartialMatches(args[0], allowed, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 2 && "admin".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("hukumfarmer.admin")) return Collections.emptyList();
            StringUtil.copyPartialMatches(args[1], ADMIN_SUBS, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 2 && "npc".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("hukumfarmer.npc")) return Collections.emptyList();
            StringUtil.copyPartialMatches(args[1], NPC_SUBS, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 3 && "admin".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("hukumfarmer.admin")) return Collections.emptyList();
            List<String> playerNames = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                playerNames.add(p.getName());
            }
            StringUtil.copyPartialMatches(args[2], playerNames, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 4 && "admin".equalsIgnoreCase(args[0])) {
            if ("language".equalsIgnoreCase(args[1]) || "lang".equalsIgnoreCase(args[1])) {
                StringUtil.copyPartialMatches(args[3], LANGUAGES, completions);
                Collections.sort(completions);
                return completions;
            }
            if ("give".equalsIgnoreCase(args[1])) {
                return Arrays.asList("1", "2", "4", "8", "16", "32", "64");
            }
            if ("setlevel".equalsIgnoreCase(args[1])) {
                return Arrays.asList("1", "5", "10", "20", "50", "100");
            }
            if ("addxp".equalsIgnoreCase(args[1])) {
                return Arrays.asList("500", "1000", "5000", "10000", "50000");
            }
        }

        if (args.length == 3 && "npc".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("hukumfarmer.npc")) return Collections.emptyList();
            if ("remove".equalsIgnoreCase(args[1]) || "teleport".equalsIgnoreCase(args[1])) {
                List<String> npcIds = new ArrayList<>();
                for (FarmerNPC npc : HukumFarmer.getInstance().getNpcManager().getAllNpcs()) {
                    npcIds.add(npc.getId());
                }
                StringUtil.copyPartialMatches(args[2], npcIds, completions);
                Collections.sort(completions);
                return completions;
            }
        }

        return Collections.emptyList();
    }
}
