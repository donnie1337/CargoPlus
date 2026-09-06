package com.cargoplus.listener;

import com.cargoplus.CargoPlus;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {
    private final CargoPlus plugin;
    public PlayerListener(CargoPlus plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { plugin.ensureUser(event.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { plugin.permissions().remove(event.getPlayer()); }
}
