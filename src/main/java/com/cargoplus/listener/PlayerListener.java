package com.cargoplus.listener;

import com.cargoplus.CargoPlus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerListener implements Listener {
    private static final int DEFAULT_AUTH_TIMEOUT_SECONDS = 60;
    private static final long AUTH_CHECK_INTERVAL_TICKS = 10L;
    private final CargoPlus plugin;
    private final Map<UUID, BukkitTask> pending = new ConcurrentHashMap<>();

    public PlayerListener(CargoPlus plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.setJoinMessage(null);
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
        }, 1L, AUTH_CHECK_INTERVAL_TICKS);
        pending.put(uuid, task);

        long timeoutTicks = Math.max(1, getAuthenticationTimeoutSeconds()) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            BukkitTask current = pending.get(uuid);
            if (current == task) {
                task.cancel();
                pending.remove(uuid, task);
                if (player.isOnline() && !plugin.isAuthenticated(player)) {
                    plugin.permissions().remove(player);
                }
            }
        }, timeoutTicks);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.setQuitMessage(null);
        UUID uuid = event.getPlayer().getUniqueId();
        cancelPending(uuid);
        plugin.permissions().remove(event.getPlayer());
    }

    private int getAuthenticationTimeoutSeconds() {
        Plugin auth = Bukkit.getPluginManager().getPlugin("LoginPlus");
        if (auth instanceof JavaPlugin javaPlugin && auth.isEnabled()) {
            return Math.max(1, javaPlugin.getConfig().getInt("tempo-limite-login-segundos", DEFAULT_AUTH_TIMEOUT_SECONDS));
        }
        return DEFAULT_AUTH_TIMEOUT_SECONDS;
    }

    private boolean isAuthSystemAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("LoginPlus");
    }

    private void cancelPending(UUID uuid) {
        BukkitTask task = pending.remove(uuid);
        if (task != null) task.cancel();
    }
}
