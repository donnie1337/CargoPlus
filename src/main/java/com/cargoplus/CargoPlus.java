package com.cargoplus;

import com.cargoplus.api.CargoPlusAPI;
import com.cargoplus.command.CargoCommand;
import com.cargoplus.listener.PlayerListener;
import com.cargoplus.model.UserData;
import com.cargoplus.service.CargoPlusColorConfig;
import com.cargoplus.service.GroupService;
import com.cargoplus.service.NicknameColorService;
import com.cargoplus.service.PermissionService;
import com.cargoplus.storage.Storage;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CargoPlus extends JavaPlugin {
    private GroupService groups;
    private Storage storage;
    private PermissionService permissions;
    private CargoPlusAPI api;
    private Map<String, String> messages;
    private NicknameColorService nicknameColors;
    private CargoPlusColorConfig chatColors;
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "CargoPlus-Save");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        loadMessages();
        try {
            GroupService loadedGroups = new GroupService(getConfig());
            Storage loadedStorage = new Storage(getDataFolder(), getConfig().getString("storage.file", "data.yml"));
            loadedStorage.load();
            CargoPlusColorConfig loadedChatColors = new CargoPlusColorConfig(getConfig());
            NicknameColorService loadedNicknameColors = new NicknameColorService(loadedChatColors);
            groups = loadedGroups;
            storage = loadedStorage;
            chatColors = loadedChatColors;
            nicknameColors = loadedNicknameColors;
            permissions = new PermissionService(this, storage, groups, nicknameColors, chatColors);
            api = new CargoPlusAPI(permissions, groups);
        } catch (Exception ex) {
            getLogger().severe("Falha ao carregar dados do CargoPlus: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        registerCommands();
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        registerApi();
        for (Player player : getServer().getOnlinePlayers()) {
            permissions.remove(player);
            if (isAuthenticated(player)) ensureUser(player);
        }
        getLogger().info("CargoPlus ativado com " + groups.all().size() + " cargos.");
    }

    private void registerCommands() {
        CargoCommand command = new CargoCommand(this);
        for (String name : new String[]{"promover", "setcargo", "removercargo", "cargo"}) {
            var registered = getCommand(name);
            if (registered != null) {
                registered.setExecutor(command);
                registered.setTabCompleter(command);
            }
        }
    }

    private void registerApi() {
        getServer().getServicesManager().unregisterAll(this);
        getServer().getServicesManager().register(CargoPlusAPI.class, api, this, ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
        if (permissions != null) permissions.clearAll();
        saveExecutor.shutdown();
        if (storage != null) {
            try { storage.save(); }
            catch (IOException ex) { getLogger().severe("Não foi possível salvar data.yml: " + ex.getMessage()); }
        }
    }

    private void loadMessages() {
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(), "messages.yml"));
        messages = new java.util.HashMap<>();
        for (String key : yaml.getKeys(false)) messages.put(key, yaml.getString(key, ""));
    }

    public String message(String key) {
        return ChatColor.translateAlternateColorCodes('&', messages.getOrDefault(key, "&cMensagem não configurada: " + key));
    }

    public GroupService groups() { return groups; }
    public PermissionService permissions() { return permissions; }
    public CargoPlusAPI api() { return api; }

    public boolean isAuthenticated(Player player) {
        if (player == null || !player.isOnline()) return false;
        Plugin auth = getServer().getPluginManager().getPlugin("AuthSystem");
        if (auth == null || !auth.isEnabled()) return false;
        try {
            Method method = auth.getClass().getMethod("isAuthenticated", Player.class);
            Object result = method.invoke(auth, player);
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }

    public void ensureUser(Player player) {
        if (!isAuthenticated(player)) {
            permissions.remove(player);
            return;
        }
        permissions.ensureUser(player);
        permissions.apply(player, permissions.getUser(player.getUniqueId()));
        saveAsync();
    }

    public void setGroup(Player player, String group) {
        permissions.setGroup(player, group);
        if (isAuthenticated(player)) permissions.apply(player, permissions.getUser(player.getUniqueId()));
        else permissions.remove(player);
        saveAsync();
    }

    public boolean setChatColor(Player player, String color) {
        if (!isAuthenticated(player)) return false;
        if (!permissions.setChatColor(player, color)) return false;
        permissions.apply(player, permissions.getUser(player.getUniqueId()));
        saveAsync();
        return true;
    }

    public Map<String, String> chatColors() { return chatColors.allowedColors(); }

    public String getChatColor(Player player) {
        return player == null ? "" : permissions.getChatColor(player.getUniqueId());
    }

    public synchronized void reloadPlugin(CommandSender sender) {
        try {
            reloadConfig();
            loadMessages();
            GroupService newGroups = new GroupService(getConfig());
            Storage newStorage = new Storage(getDataFolder(), getConfig().getString("storage.file", "data.yml"));
            newStorage.load();
            CargoPlusColorConfig newChatColors = new CargoPlusColorConfig(getConfig());
            NicknameColorService newNicknameColors = new NicknameColorService(newChatColors);
            PermissionService newPermissions = new PermissionService(this, newStorage, newGroups, newNicknameColors, newChatColors);
            CargoPlusAPI newApi = new CargoPlusAPI(newPermissions, newGroups);

            PermissionService oldPermissions = permissions;
            groups = newGroups;
            storage = newStorage;
            chatColors = newChatColors;
            nicknameColors = newNicknameColors;
            permissions = newPermissions;
            api = newApi;

            if (oldPermissions != null) oldPermissions.clearAll();
            for (Player player : getServer().getOnlinePlayers()) {
                permissions.remove(player);
                if (isAuthenticated(player)) {
                    permissions.ensureUser(player);
                    permissions.apply(player, permissions.getUser(player.getUniqueId()));
                }
            }
            registerApi();
            saveAsync();
            sender.sendMessage(message("reloaded"));
        } catch (Exception ex) {
            getLogger().severe("Reload abortado: " + ex.getMessage());
            sender.sendMessage("§cNão foi possível recarregar o CargoPlus. Verifique o console.");
        }
    }

    private void saveAsync() {
        final Storage currentStorage = storage;
        final Map<java.util.UUID, UserData> snapshot = currentStorage.snapshot();
        saveExecutor.execute(() -> {
            try { currentStorage.saveSnapshot(snapshot); }
            catch (IOException ex) { getLogger().warning("Falha ao salvar dados: " + ex.getMessage()); }
        });
    }
}
