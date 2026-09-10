package com.cargoplus;

import com.cargoplus.api.CargoPlusAPI;
import com.cargoplus.command.CargoCommand;
import com.cargoplus.listener.CommandGuardListener;
import com.cargoplus.listener.PlayerListener;
import com.cargoplus.model.UserData;
import com.cargoplus.service.CargoPlusColorConfig;
import com.cargoplus.service.GroupService;
import com.cargoplus.service.NicknameColorService;
import com.cargoplus.service.PermissionService;
import com.cargoplus.service.PrefixAnimationService;
import com.cargoplus.storage.Storage;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class CargoPlus extends JavaPlugin {
    private GroupService groups;
    private Storage storage;
    private PermissionService permissions;
    private CargoPlusAPI api;
    private Map<String, String> messages;
    private NicknameColorService nicknameColors;
    private CargoPlusColorConfig chatColors;
    private PrefixAnimationService prefixAnimation;
    private BukkitTask animatedPrefixTask;
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "CargoPlus-Save");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureMessagesFile();
        loadMessages();
        try {
            PrefixAnimationService animation = new PrefixAnimationService(getConfig());
            CargoPlusColorConfig loadedColors = new CargoPlusColorConfig(getConfig());
            GroupService loadedGroups = new GroupService(getConfig(), loadedColors, animation);
            Storage loadedStorage = new Storage(getDataFolder(), getConfig().getString("storage.file", "data.yml"));
            loadedStorage.load();
            NicknameColorService loadedNicknameColors = new NicknameColorService(loadedColors, animation);
            groups = loadedGroups;
            storage = loadedStorage;
            chatColors = loadedColors;
            prefixAnimation = animation;
            nicknameColors = loadedNicknameColors;
            permissions = new PermissionService(this, storage, groups, loadedNicknameColors, loadedColors);
            api = new CargoPlusAPI(permissions, groups, animation);
        } catch (Exception ex) {
            getLogger().severe("Falha ao carregar dados do CargoPlus: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        registerCommands();
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandGuardListener(this), this);
        registerApi();
        protectAdministrativeIdentities();
        for (Player player : getServer().getOnlinePlayers()) {
            permissions.remove(player);
            if (isAuthenticated(player)) ensureUser(player);
        }
        startAnimatedPrefixTask();
        getLogger().info("CargoPlus ativado com " + groups.all().size() + " cargos.");
    }

    private void startAnimatedPrefixTask() {
        stopAnimatedPrefixTask();
        animatedPrefixTask = getServer().getScheduler().runTaskTimer(this, () -> {
            if (permissions == null || nicknameColors == null || groups == null || prefixAnimation == null) return;
            for (Player player : getServer().getOnlinePlayers()) {
                if (!isAuthenticated(player)) continue;
                String group = permissions.getGroup(player.getUniqueId());
                nicknameColors.refreshAnimatedPrefix(player, group, groups);
            }
        }, 1L, 2L);
    }

    private void stopAnimatedPrefixTask() {
        if (animatedPrefixTask != null) {
            animatedPrefixTask.cancel();
            animatedPrefixTask = null;
        }
    }

    private void ensureMessagesFile() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) saveResource("messages.yml", false);
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

    private void protectAdministrativeIdentities() {
        if (groups == null || storage == null || groups.hierarchy().isEmpty()) return;
        String topGroup = groups.hierarchy().get(groups.hierarchy().size() - 1);
        for (UserData user : storage.snapshot().values()) {
            if (user == null || !topGroup.equalsIgnoreCase(user.group())) continue;
            protectLoginIdentity(user.name(), user.uuid());
        }
    }

    private void protectLoginIdentity(String username, UUID uuid) {
        if (username == null || username.isBlank() || uuid == null) return;
        Plugin loginPlus = getServer().getPluginManager().getPlugin("LoginPlus");
        if (loginPlus == null || !loginPlus.isEnabled()) return;
        try {
            Method method = loginPlus.getClass().getMethod("protectIdentity", String.class, UUID.class);
            method.invoke(loginPlus, username, uuid);
        } catch (ReflectiveOperationException | LinkageError ex) {
            getLogger().warning("Nao foi possivel proteger a identidade administrativa " + username + " no LoginPlus: " + ex.getMessage());
        }
    }

    @Override
    public void onDisable() {
        stopAnimatedPrefixTask();
        if (permissions != null) permissions.clearAll();
        if (storage != null) {
            try {
                saveExecutor.shutdown();
                if (!saveExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    saveExecutor.shutdownNow();
                    if (!saveExecutor.awaitTermination(2, TimeUnit.SECONDS)) getLogger().warning("A fila de salvamento do CargoPlus nao terminou antes do desligamento.");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                saveExecutor.shutdownNow();
            }
            try { storage.save(); }
            catch (IOException ex) { getLogger().severe("Não foi possível salvar data.yml: " + ex.getMessage()); }
        } else saveExecutor.shutdownNow();
    }

    private void loadMessages() {
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        messages = new java.util.HashMap<>();
        for (String key : yaml.getKeys(false)) messages.put(key, yaml.getString(key, ""));
    }

    public String message(String key) { return ChatColor.translateAlternateColorCodes('&', messages.getOrDefault(key, "&cMensagem não configurada: " + key)); }
    public GroupService groups() { return groups; }
    public PermissionService permissions() { return permissions; }
    public CargoPlusAPI api() { return api; }
    public String getCargoDisplayName(String group) { if (group == null || groups == null || groups.get(group) == null) return group == null ? "" : group; return groups.get(group).displayName(); }
    public String getCargoColor(String group) { if (group == null || groups == null || chatColors == null) return ChatColor.WHITE.toString(); ChatColor color = chatColors.resolve(groups.nameColor(group)); return color == null ? ChatColor.WHITE.toString() : color.toString(); }
    public boolean receivesJoinQuitMessage(String group) { return groups != null && group != null && !groups.defaultGroup().equalsIgnoreCase(group.trim()); }

    public boolean isAuthenticated(Player player) {
        if (player == null || !player.isOnline()) return false;
        Plugin auth = getServer().getPluginManager().getPlugin("LoginPlus");
        if (auth == null || !auth.isEnabled()) return false;
        try {
            Method method = auth.getClass().getMethod("isAuthenticated", Player.class);
            Object result = method.invoke(auth, player);
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException | LinkageError ex) { return false; }
    }

    public void ensureUser(Player player) {
        if (!isAuthenticated(player)) { permissions.remove(player); return; }
        permissions.ensureUser(player);
        permissions.apply(player, permissions.getUser(player.getUniqueId()));
        saveAsync();
    }

    public void setGroup(Player player, String group) {
        permissions.setGroup(player, group);
        if (groups != null && !groups.hierarchy().isEmpty()) {
            String topGroup = groups.hierarchy().get(groups.hierarchy().size() - 1);
            if (topGroup.equalsIgnoreCase(group)) protectLoginIdentity(player.getName(), player.getUniqueId());
        }
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
    public String getChatColor(Player player) { return player == null ? "" : permissions.getChatColor(player.getUniqueId()); }

    public void reloadPlugin(CommandSender sender) {
        try {
            reloadConfig();
            ensureMessagesFile();
            loadMessages();
            PrefixAnimationService newAnimation = new PrefixAnimationService(getConfig());
            CargoPlusColorConfig newChatColors = new CargoPlusColorConfig(getConfig());
            GroupService newGroups = new GroupService(getConfig(), newChatColors, newAnimation);
            Storage newStorage = new Storage(getDataFolder(), getConfig().getString("storage.file", "data.yml"));
            newStorage.load();
            NicknameColorService newNicknameColors = new NicknameColorService(newChatColors, newAnimation);
            PermissionService newPermissions = new PermissionService(this, newStorage, newGroups, newNicknameColors, newChatColors);
            CargoPlusAPI newApi = new CargoPlusAPI(newPermissions, newGroups, newAnimation);

            PermissionService oldPermissions = permissions;
            stopAnimatedPrefixTask();
            groups = newGroups;
            storage = newStorage;
            chatColors = newChatColors;
            prefixAnimation = newAnimation;
            nicknameColors = newNicknameColors;
            permissions = newPermissions;
            api = newApi;

            if (oldPermissions != null) oldPermissions.clearAll();
            protectAdministrativeIdentities();
            for (Player player : getServer().getOnlinePlayers()) {
                permissions.remove(player);
                if (isAuthenticated(player)) {
                    permissions.ensureUser(player);
                    permissions.apply(player, permissions.getUser(player.getUniqueId()));
                }
            }
            registerApi();
            startAnimatedPrefixTask();
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
        try {
            saveExecutor.execute(() -> {
                try { currentStorage.saveSnapshot(snapshot); }
                catch (IOException ex) { getLogger().warning("Falha ao salvar dados: " + ex.getMessage()); }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            try { currentStorage.saveSnapshot(snapshot); }
            catch (IOException ex) { getLogger().warning("Falha ao salvar dados: " + ex.getMessage()); }
        }
    }
}
