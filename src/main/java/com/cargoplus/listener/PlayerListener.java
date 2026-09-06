package com.cargoplus.listener;

import com.cargoplus.CargoPlus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerListener implements Listener {
    private static final long AUTH_TIMEOUT_TICKS = 20L * 90L;
    private final CargoPlus plugin;
    private final Map<UUID, BukkitTask> pending = new ConcurrentHashMap<>();

    public PlayerListener(CargoPlus plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.permissions().remove(player);
        scheduleAuthenticationCheck(player);
    }

    private void scheduleAuthenticationCheck(Player player) {
        cancelPending(player.getUniqueId());
        UUID uuid = player.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !isAuthSystemAvailable()) {
                if (!player.isOnline()) cancelPending(uuid);
                return;
            }
            if (plugin.isAuthenticated(player)) {
                plugin.ensureUser(player);
                cancelPending(uuid);
            }
        }, 1L, 2L);
        pending.put(uuid, task);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            BukkitTask current = pending.get(uuid);
            if (current == task) {
                task.cancel();
                pending.remove(uuid, task);
                if (player.isOnline() && !plugin.isAuthenticated(player)) {
                    plugin.permissions().remove(player);
                }
            }
        }, AUTH_TIMEOUT_TICKS);
    }

    private boolean isAuthSystemAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("AuthSystem");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cancelPending(uuid);
        plugin.permissions().remove(event.getPlayer());
    }

    private void cancelPending(UUID uuid) {
        BukkitTask task = pending.remove(uuid);
        if (task != null) task.cancel();
    }
}
