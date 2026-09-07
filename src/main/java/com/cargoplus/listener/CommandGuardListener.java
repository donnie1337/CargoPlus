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
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Camada central de segurança dos comandos executados por jogadores.
 *
 * O console nunca passa por estes eventos e continua podendo executar tudo.
 * Jogadores só podem executar comandos que possuam uma permissão Bukkit
 * registrada e que eles realmente tenham. Comandos sem permissão explícita
 * são negados por padrão.
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

        // Reload de qualquer plugin/servidor é exclusivamente do console.
        if (label.equals("reload") || containsReloadArgument(parts)) {
            deny(player);
            event.setCancelled(true);
            return;
        }

        Command command = findCommand(label);
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
        Set<String> commands = event.getCommands();
        Iterator<String> iterator = commands.iterator();

        while (iterator.hasNext()) {
            String label = normalize(iterator.next());
            if (label.equals("reload")) {
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

    private boolean containsReloadArgument(String[] parts) {
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].equalsIgnoreCase("reload")) return true;
        }
        return false;
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
            plugin.getLogger().warning("Não foi possível consultar o mapa de comandos: " + ex.getMessage());
        }
        return null;
    }

    private void deny(Player player) {
        player.sendMessage(plugin.message("no-permission"));
    }
}
