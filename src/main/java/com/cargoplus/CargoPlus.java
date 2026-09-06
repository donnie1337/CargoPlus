package com.cargoplus;

import com.cargoplus.api.CargoPlusAPI;
import com.cargoplus.command.CargoCommand;
import com.cargoplus.listener.PlayerListener;
import com.cargoplus.model.UserData;
import com.cargoplus.service.GroupService;
import com.cargoplus.service.PermissionService;
import com.cargoplus.storage.Storage;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CargoPlus extends JavaPlugin {
    private GroupService groups;
    private Storage storage;
    private PermissionService permissions;
    private CargoPlusAPI api;
    private Map<String, String> messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        loadMessages();
        try {
            groups = new GroupService(getConfig());
            storage = new Storage(getDataFolder(), getConfig().getString("storage.file", "data.yml"));
            storage.load();
        } catch (Exception ex) {
            getLogger().severe("Falha ao carregar dados do CargoPlus: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        permissions = new PermissionService(this, storage, groups);
        api = new CargoPlusAPI(permissions, groups);
        CargoCommand command = new CargoCommand(this);
        getCommand("cargo").setExecutor(command);
        getCommand("cargo").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getServicesManager().register(CargoPlusAPI.class, api, this, ServicePriority.Normal);
        for (Player player : getServer().getOnlinePlayers()) ensureUser(player);
        getLogger().info("CargoPlus ativado com " + groups.all().size() + " cargos.");
    }

    @Override
    public void onDisable() {
        if (permissions != null) permissions.clearAll();
        if (storage != null) {
            try { storage.save(); } catch (IOException ex) { getLogger().severe("Não foi possível salvar data.yml: " + ex.getMessage()); }
        }
    }

    private void loadMessages() {
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(), "messages.yml"));
        messages = new java.util.HashMap<>();
        for (String key : yaml.getKeys(false)) messages.put(key, yaml.getString(key, ""));
    }

    public String message(String key) { return ChatColor.translateAlternateColorCodes('&', messages.getOrDefault(key, "&cMensagem não configurada: " + key)); }
    public GroupService groups() { return groups; }
    public PermissionService permissions() { return permissions; }
    public CargoPlusAPI api() { return api; }

    public void ensureUser(Player player) {
        permissions.ensureUser(player);
        saveAsync();
    }

    public void setGroup(Player player, String group) {
        permissions.setGroup(player, group);
        saveAsync();
    }

    public void reloadPlugin(CommandSender sender) {
        try {
            permissions.clearAll();
            reloadConfig();
            loadMessages();
            GroupService newGroups = new GroupService(getConfig());
            storage.load();
            groups = newGroups;
            permissions = new PermissionService(this, storage, groups);
            api = new CargoPlusAPI(permissions, groups);
            getServer().getServicesManager().register(CargoPlusAPI.class, api, this, ServicePriority.Normal);
            for (Player player : getServer().getOnlinePlayers()) permissions.ensureUser(player);
            saveAsync();
            sender.sendMessage(message("reloaded"));
        } catch (Exception ex) {
            getLogger().severe("Reload abortado: " + ex.getMessage());
            sender.sendMessage("§cNão foi possível recarregar o CargoPlus. Verifique o console.");
        }
    }

    private void saveAsync() {
        Map<UUID, UserData> snapshot = storage.snapshot();
        CompletableFuture.runAsync(() -> {
            try { storage.saveSnapshot(snapshot); }
            catch (IOException ex) { getLogger().warning("Falha ao salvar dados: " + ex.getMessage()); }
        });
    }
}
