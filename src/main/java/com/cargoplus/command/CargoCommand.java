package com.cargoplus.command;

import com.cargoplus.CargoPlus;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public final class CargoCommand implements CommandExecutor, TabCompleter {
    private final CargoPlus plugin;

    public CargoCommand(CargoPlus plugin) { this.plugin = plugin; }

    private String msg(String key) { return plugin.message(key); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !plugin.isAuthenticated(player)) {
            sender.sendMessage(msg("not-authenticated"));
            return true;
        }
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("cargo")) {
            if (args.length == 0) { sender.sendMessage(msg("usage")); return true; }
            return executeSubcommand(sender, args[0].toLowerCase(Locale.ROOT), Arrays.copyOfRange(args, 1, args.length));
        }
        return executeSubcommand(sender, commandName, args);
    }

    private boolean hasCargoPermission(CommandSender sender, String permission) {
        return !(sender instanceof Player) || sender.hasPermission(permission);
    }

    private boolean executeSubcommand(CommandSender sender, String sub, String[] args) {
        return switch (sub) {
            case "promover" -> promote(sender, args);
            case "setcargo" -> setCargo(sender, args);
            case "removercargo" -> removeCargo(sender, args);
            case "reload" -> reload(sender);
            default -> { sender.sendMessage(msg("usage")); yield true; }
        };
    }

    private boolean reload(CommandSender sender) {
        if (!hasCargoPermission(sender, "cargoplus.admin")) { sender.sendMessage(msg("no-permission")); return true; }
        plugin.reloadPlugin(sender);
        return true;
    }

    private boolean promote(CommandSender sender, String[] args) {
        if (!hasCargoPermission(sender, "cargoplus.promover")) { sender.sendMessage(msg("no-permission")); return true; }
        if (args.length != 1) { sender.sendMessage(msg("usage-promover")); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { sender.sendMessage(msg("user-not-found")); return true; }
        String current = plugin.permissions().getGroup(target.getUniqueId());
        if (plugin.groups().indexOf(current) < 0) { sender.sendMessage(msg("invalid-group")); return true; }
        String next = plugin.groups().next(current);
        if (next == null) { sender.sendMessage(msg("already-top")); return true; }

        var nextGroup = plugin.groups().get(next);
        plugin.setGroup(target, next);

        String promotedMessage = msg("promotion-title")
                .replace("{player}", target.getName())
                .replace("{group}", nextGroup.displayName());
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (plugin.isAuthenticated(online)) {
                online.sendTitle(promotedMessage, "", 10, 70, 20);
            }
        }

        sender.sendMessage(msg("promoted")
                .replace("{player}", target.getName())
                .replace("{group}", nextGroup.displayName()));
        return true;
    }

    private boolean setCargo(CommandSender sender, String[] args) {
        if (sender instanceof Player player && !player.isOp()) {
            sender.sendMessage(msg("no-permission"));
            return true;
        }
        if (!hasCargoPermission(sender, "cargoplus.setcargo")) { sender.sendMessage(msg("no-permission")); return true; }
        if (args.length != 2) { sender.sendMessage(msg("usage-setcargo")); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { sender.sendMessage(msg("user-not-found")); return true; }
        var group = plugin.groups().get(args[1]);
        if (group == null) { sender.sendMessage(msg("group-not-found")); return true; }
        plugin.setGroup(target, group.name());
        sender.sendMessage(msg("assigned").replace("{player}", target.getName()).replace("{group}", group.displayName()));
        return true;
    }

    private boolean removeCargo(CommandSender sender, String[] args) {
        if (!hasCargoPermission(sender, "cargoplus.removercargo")) { sender.sendMessage(msg("no-permission")); return true; }
        if (args.length != 1) { sender.sendMessage(msg("usage-removercargo")); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { sender.sendMessage(msg("user-not-found")); return true; }

        if (sender instanceof Player player) {
            if (player.getUniqueId().equals(target.getUniqueId())) {
                sender.sendMessage(msg("cannot-remove-self"));
                return true;
            }

            String executorGroup = plugin.permissions().getGroup(player.getUniqueId());
            String targetGroup = plugin.permissions().getGroup(target.getUniqueId());
            int executorRank = plugin.groups().indexOf(executorGroup);
            int targetRank = plugin.groups().indexOf(targetGroup);

            // Só é permitido remover cargos abaixo do próprio cargo.
            if (executorRank < 0 || targetRank < 0 || targetRank >= executorRank) {
                sender.sendMessage(msg("cannot-remove-higher"));
                return true;
            }
        }

        plugin.setGroup(target, plugin.groups().defaultGroup());
        sender.sendMessage(msg("removed").replace("{player}", target.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("cargo")) {
            if (args.length == 1) return partial(List.of("promover", "setcargo", "removercargo", "reload"), args[0]);
            if (args.length == 2 && List.of("promover", "removercargo", "setcargo").contains(args[0].toLowerCase(Locale.ROOT))) return onlinePlayers(args[1]);
            if (args.length == 3 && args[0].equalsIgnoreCase("setcargo")) return partial(plugin.groups().hierarchy(), args[2]);
            return List.of();
        }
        if (commandName.equals("promover") || commandName.equals("removercargo")) return args.length == 1 ? onlinePlayers(args[0]) : List.of();
        if (commandName.equals("setcargo")) {
            if (args.length == 1) return onlinePlayers(args[0]);
            if (args.length == 2) return partial(plugin.groups().hierarchy(), args[1]);
        }
        return List.of();
    }

    private List<String> onlinePlayers(String input) { return partial(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), input); }
    private List<String> partial(Collection<String> values, String input) {
        return values.stream().filter(v -> v.regionMatches(true, 0, input, 0, input.length())).sorted().limit(20).toList();
    }
}
