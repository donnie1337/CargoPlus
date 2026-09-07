package com.cargoplus.listener;

import com.cargoplus.CargoPlus;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;

/**
 * Camada central de seguranca dos comandos executados por jogadores.
 *
 * O console nunca passa por estes eventos e continua podendo executar tudo.
 * Jogadores so podem executar comandos que possuam uma permissao Bukkit
 * registrada e que eles realmente tenham. Comandos sem permissao explicita
 * sao negados por padrao.
 */
public final class CommandGuardListener implements Listener {
    private final CargoPlus plugin;

    public CommandGuardListener(CargoPlus plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String[] parts = event.getMessage().trim().split("\\s+");
        if (parts.length == 0) return;

        String label = normalize(parts[0]);
        Command command = findCommand(label);

        // Reload do servidor e dos plugins e sempre exclusivo do console.
        if (isServerReload(label) || isPluginReload(label, parts, command)) {
            deny(player);
            event.setCancelled(true);
            return;
        }

        if (command == null) {
            deny(player);
            event.setCancelled(true);
            return;
        }

        String permission = command.getPermission();
        if (permission == null || permission.isBlank() || !player.hasPermission(permission)) {
            deny(player);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        Collection<String> commands = event.getCommands();
        Iterator<String> iterator = commands.iterator();

        while (iterator.hasNext()) {
            String label = normalize(iterator.next());
            if (isServerReload(label)) {
                iterator.remove();
                continue;
            }

            Command command = findCommand(label);
            if (command == null) {
                iterator.remove();
                continue;
            }

            String permission = command.getPermission();
            if (permission == null || permission.isBlank() || !player.hasPermission(permission)) {
                iterator.remove();
            }
        }
    }

    private boolean isServerReload(String label) {
        return switch (label) {
            case "reload", "rl", "bukkit:reload", "spigot:reload", "paper:reload", "minecraft:reload" -> true;
            default -> false;
        };
    }

    private boolean isPluginReload(String label, String[] parts, Command command) {
        if (parts.length < 2 || !parts[1].equalsIgnoreCase("reload") || command == null) return false;
        String permission = command.getPermission();
        if (permission == null || permission.isBlank()) return false;
        String normalizedPermission = permission.toLowerCase(Locale.ROOT);
        return normalizedPermission.contains("admin") || normalizedPermission.contains("reload");
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        return normalized;
    }

    private Command findCommand(String label) {
        String normalized = normalize(label);
        try {
            Method getCommandMap = Bukkit.getServer().getClass().getMethod("getCommandMap");
            Object result = getCommandMap.invoke(Bukkit.getServer());
            if (!(result instanceof CommandMap commandMap)) return null;

            Command command = commandMap.getCommand(normalized);
            if (command != null) return command;

            int separator = normalized.indexOf(':');
            if (separator >= 0 && separator + 1 < normalized.length()) {
                return commandMap.getCommand(normalized.substring(separator + 1));
            }
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("Nao foi possivel consultar o mapa de comandos: " + ex.getMessage());
        }
        return null;
    }

    private void deny(Player player) {
        player.sendMessage(plugin.message("no-permission"));
    }
}
