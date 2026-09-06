package com.cargoplus.command;

import com.cargoplus.CargoPlus;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public final class CargoCommand implements CommandExecutor, TabCompleter {
    private final CargoPlus plugin;

    public CargoCommand(CargoPlus plugin) {
        this.plugin = plugin;
    }

    private String msg(String key) {
        return plugin.message(key);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !plugin.isAuthenticated(player)) {
            sender.sendMessage(msg("not-authenticated"));
            return true;
        }

        String commandName = command.getName().toLowerCase(Locale.ROOT);

        if (commandName.equals("cargo")) {
            if (args.length == 0) {
                sender.sendMessage(msg("usage"));
                return true;
            }

            String sub = args[0].toLowerCase(Locale.ROOT);
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            return executeSubcommand(sender, sub, subArgs);
        }

        return executeSubcommand(sender, commandName, args);
    }

    private boolean hasCargoPermission(CommandSender sender, String permission) {
        if (!(sender instanceof Player)) {
            return true;
        }
        return sender.hasPermission(permission);
    }

    private boolean executeSubcommand(CommandSender sender, String sub, String[] args) {
        return switch (sub) {
            case "promover" -> promote(sender, args);
            case "setcargo" -> setCargo(sender, args);
            case "removercargo" -> removeCargo(sender, args);
            case "reload" -> reload(sender);
            default -> {
                sender.sendMessage(msg("usage"));
                yield true;
            }
        };
    }

    private boolean reload(CommandSender sender) {
        if (!hasCargoPermission(sender, "cargoplus.admin")) {
            sender.sendMessage(msg("no-permission"));
            return true;
        }
        plugin.reloadPlugin(sender);
        return true;
    }

    private boolean promote(CommandSender sender, String[] args) {
        if (!hasCargoPermission(sender, "cargoplus.promover")) {
            sender.sendMessage(msg("no-permission"));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(msg("usage-promover"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(msg("user-not-found"));
            return true;
        }

        String current = plugin.permissions().getGroup(target.getUniqueId());
        int currentIndex = plugin.groups().indexOf(current);
        if (currentIndex < 0) {
            sender.sendMessage(msg("invalid-group"));
            return true;
        }

        String next = plugin.groups().next(current);
        if (next == null) {
            sender.sendMessage(msg("already-top"));
            return true;
        }

        plugin.setGroup(target, next);
        sender.sendMessage(msg("promoted").replace("{player}", target.getName())
                .replace("{group}", plugin.groups().get(next).displayName()));
        return true;
    }

    private boolean setCargo(CommandSender sender, String[] args) {
        if (!hasCargoPermission(sender, "cargoplus.setcargo")) {
            sender.sendMessage(msg("no-permission"));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(msg("usage-setcargo"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(msg("user-not-found"));
            return true;
        }

        var group = plugin.groups().get(args[1]);
        if (group == null) {
            sender.sendMessage(msg("group-not-found"));
            return true;
        }

        plugin.setGroup(target, group.name());
        sender.sendMessage(msg("assigned").replace("{player}", target.getName())
                .replace("{group}", group.displayName()));
        return true;
    }

    private boolean removeCargo(CommandSender sender, String[] args) {
        if (!hasCargoPermission(sender, "cargoplus.removercargo")) {
            sender.sendMessage(msg("no-permission"));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(msg("usage-removercargo"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(msg("user-not-found"));
            return true;
        }

        // Jogadores não podem remover o próprio cargo. O console continua podendo fazê-lo.
        if (sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(msg("cannot-remove-self"));
            return true;
        }

        plugin.setGroup(target, plugin.groups().defaultGroup());
        sender.sendMessage(msg("removed").replace("{player}", target.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);

        if (commandName.equals("cargo")) {
            if (args.length == 1) {
                return partial(List.of("promover", "setcargo", "removercargo", "reload"), args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("promover")) {
                return onlinePlayers(args[1]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("removercargo")) {
                return onlinePlayers(args[1]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("setcargo")) {
                return onlinePlayers(args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("setcargo")) {
                return partial(plugin.groups().hierarchy(), args[2]);
            }
            return List.of();
        }

        if (commandName.equals("promover") || commandName.equals("removercargo")) {
            if (args.length == 1) return onlinePlayers(args[0]);
            return List.of();
        }

        if (commandName.equals("setcargo")) {
            if (args.length == 1) return onlinePlayers(args[0]);
            if (args.length == 2) return partial(plugin.groups().hierarchy(), args[1]);
        }

        return List.of();
    }

    private List<String> onlinePlayers(String input) {
        return partial(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), input);
    }

    private List<String> partial(Collection<String> values, String input) {
        return values.stream()
                .filter(v -> v.regionMatches(true, 0, input, 0, input.length()))
                .sorted()
                .limit(20)
                .toList();
    }
}
