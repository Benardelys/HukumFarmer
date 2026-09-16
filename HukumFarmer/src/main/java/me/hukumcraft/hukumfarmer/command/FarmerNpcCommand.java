package me.hukumcraft.hukumfarmer.command;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.model.FarmerNPC;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Handles NPC management subcommands (/farmer npc ...).
 */
public class FarmerNpcCommand {

    private final HukumFarmer plugin;

    public FarmerNpcCommand(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    public void handle(CommandSender sender, String[] args) {
        Player player = sender instanceof Player p ? p : null;
        if (!sender.hasPermission("hukumfarmer.npc")) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
            return;
        }

        if (args.length < 2) {
            sendNpcHelp(sender);
            return;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "create", "olustur" -> handleCreate(sender, args);
            case "remove", "sil", "delete" -> handleRemove(sender, args);
            case "list", "liste" -> handleList(sender);
            case "teleport", "tp", "git" -> handleTeleport(sender, args);
            default -> sendNpcHelp(sender);
        }
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage((Player) null, "player-only"));
            return;
        }

        if (!player.hasPermission("hukumfarmer.npc.create")) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
            return;
        }

        Location loc = player.getLocation();
        if (loc.getWorld() == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-invalid-world"));
            return;
        }

        String id = (args.length >= 3 && !args[2].trim().isEmpty()) ? args[2].trim().toLowerCase() : "npc_" + (plugin.getNpcManager().getAllNpcs().size() + 1);

        if (plugin.getNpcManager().getNpc(id).isPresent()) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-already-exists", "%id%", id));
            return;
        }

        try {
            FarmerNPC npc = plugin.getNpcManager().createNpc(id, loc, null, null);
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-created", "%id%", npc.getId()));
            plugin.getLogger().info("NPC '" + npc.getId() + "' successfully created by " + player.getName() + " at " + loc);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create NPC for " + player.getName(), e);
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-create-error", "%error%", e.getMessage()));
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        Player player = sender instanceof Player p ? p : null;
        if (!sender.hasPermission("hukumfarmer.npc.remove")) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
            return;
        }

        String id;
        if (args.length >= 3) {
            id = args[2];
        } else {
            if (player == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-usage-remove", "%id%", "<id>"));
                return;
            }
            Optional<FarmerNPC> nearest = plugin.getNpcManager().getNearestNpc(player.getLocation(), 6.0);
            if (nearest.isEmpty()) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-not-found"));
                return;
            }
            id = nearest.get().getId();
        }

        boolean removed = plugin.getNpcManager().removeNpc(id);
        if (removed) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-removed", "%id%", id));
            plugin.getLogger().info("NPC '" + id + "' was removed by " + sender.getName());
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-not-found"));
        }
    }

    private void handleList(CommandSender sender) {
        Player player = sender instanceof Player p ? p : null;
        if (!sender.hasPermission("hukumfarmer.npc.list")) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
            return;
        }

        Collection<FarmerNPC> npcs = plugin.getNpcManager().getAllNpcs();
        if (npcs.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-list-empty"));
            return;
        }

        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-list-header"));
        for (FarmerNPC npc : npcs) {
            Location loc = npc.getLocation();
            String world = loc != null && loc.getWorld() != null ? loc.getWorld().getName() : "Unknown";
            String x = loc != null ? String.valueOf(loc.getBlockX()) : "0";
            String y = loc != null ? String.valueOf(loc.getBlockY()) : "0";
            String z = loc != null ? String.valueOf(loc.getBlockZ()) : "0";

            sender.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-list-item",
                    "%id%", npc.getId(),
                    "%world%", world,
                    "%x%", x,
                    "%y%", y,
                    "%z%", z));
        }
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage((Player) null, "player-only"));
            return;
        }

        if (!player.hasPermission("hukumfarmer.npc.teleport")) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "no-permission"));
            return;
        }

        if (args.length < 3) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-usage-teleport", "%id%", "<id>"));
            return;
        }

        String id = args[2];
        Optional<FarmerNPC> opt = plugin.getNpcManager().getNpc(id);
        if (opt.isEmpty() || opt.get().getLocation() == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-not-found"));
            return;
        }

        Location loc = opt.get().getLocation();
        if (loc.getWorld() == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-invalid-world"));
            return;
        }

        player.teleport(loc);
        player.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-teleported", "%id%", id));
    }

    private void sendNpcHelp(CommandSender sender) {
        Player player = sender instanceof Player p ? p : null;
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-help-header"));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-help-create"));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-help-remove"));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-help-list"));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-help-teleport"));
        sender.sendMessage(plugin.getLanguageManager().getMessage(player, "npc-help-footer"));
    }
}
