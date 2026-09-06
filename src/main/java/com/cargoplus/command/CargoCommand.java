package com.cargoplus.command;

import com.cargoplus.CargoPlus;
import com.cargoplus.model.UserData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public final class CargoCommand implements CommandExecutor, TabCompleter {
    private final CargoPlus plugin;

    public CargoCommand(CargoPlus plugin) { this.plugin = plugin; }

    private String msg(String key) { return plugin.message(key); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cargoplus.admin") && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(msg("no-permission")); return true;
        }
        if (args.length == 0) { sender.sendMessage(msg("usage")); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> plugin.reloadPlugin(sender);
            case "usuario" -> user(sender, args);
            case "promover" -> promote(sender, args, true);
            case "rebaixar" -> promote(sender, args, false);
            case "grupo" -> group(sender, args);
            default -> sender.sendMessage(msg("usage"));
        }
        return true;
    }

    private void user(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("set")) { sender.sendMessage("§eUse: /cargo usuario <jogador> set <cargo>"); return; }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) { sender.sendMessage(msg("user-not-found")); return; }
        if (args.length < 4 || plugin.groups().get(args[3]) == null) { sender.sendMessage(msg("group-not-found")); return; }
        plugin.setGroup(target, args[3]);
        sender.sendMessage(msg("assigned").replace("{player}", target.getName()).replace("{group}", plugin.groups().get(args[3]).displayName()));
    }

    private void promote(CommandSender sender, String[] args, boolean up) {
        if (args.length < 2) { sender.sendMessage("§eUse: /cargo " + (up ? "promover" : "rebaixar") + " <jogador>"); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(msg("user-not-found")); return; }
        String current = plugin.permissions().getGroup(target.getUniqueId());
        String next = up ? plugin.groups().next(current) : plugin.groups().previous(current);
        if (next == null) { sender.sendMessage(msg(up ? "already-top" : "already-bottom")); return; }
        plugin.setGroup(target, next);
        sender.sendMessage(msg(up ? "promoted" : "demoted").replace("{player}", target.getName()).replace("{group}", plugin.groups().get(next).displayName()));
    }

    private void group(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§eUse: /cargo grupo <lista|info> [cargo]"); return; }
        if (args[1].equalsIgnoreCase("lista")) {
            sender.sendMessage(ChatColor.GOLD + "Cargos: " + String.join(", ", plugin.groups().hierarchy()));
            return;
        }
        if (args[1].equalsIgnoreCase("info") && args.length >= 3) {
            var g = plugin.groups().get(args[2]);
            if (g == null) { sender.sendMessage(msg("group-not-found")); return; }
            sender.sendMessage(ChatColor.GOLD + g.displayName() + ChatColor.GRAY + " | prefix=" + g.prefix());
            sender.sendMessage(ChatColor.GRAY + "Permissões: " + String.join(", ", plugin.groups().resolvePermissions(g.name())));
            return;
        }
        sender.sendMessage("§eUse: /cargo grupo <lista|info> [cargo]");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return partial(List.of("grupo", "usuario", "promover", "rebaixar", "reload"), args[0]);
        if (args[0].equalsIgnoreCase("grupo") && args.length == 2) return partial(List.of("lista", "info"), args[1]);
        if (args[0].equalsIgnoreCase("usuario") && args.length == 2) return partial(List.of("set"), args[1]);
        if (args[0].equalsIgnoreCase("usuario") && args.length == 3) return onlinePlayers(args[2]);
        if (args[0].equalsIgnoreCase("usuario") && args.length == 4) return partial(plugin.groups().hierarchy(), args[3]);
        if ((args[0].equalsIgnoreCase("promover") || args[0].equalsIgnoreCase("rebaixar")) && args.length == 2) return onlinePlayers(args[1]);
        if (args[0].equalsIgnoreCase("grupo") && args.length == 3 && args[1].equalsIgnoreCase("info")) return partial(plugin.groups().hierarchy(), args[2]);
        return List.of();
    }

    private List<String> onlinePlayers(String input) { return partial(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), input); }
    private List<String> partial(Collection<String> values, String input) { return values.stream().filter(v -> v.regionMatches(true, 0, input, 0, input.length())).sorted().limit(20).toList(); }
}
