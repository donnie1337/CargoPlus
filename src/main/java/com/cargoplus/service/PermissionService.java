package com.cargoplus.service;

import com.cargoplus.model.UserData;
import com.cargoplus.storage.Storage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.permissions.PermissionAttachment;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PermissionService {
    private final JavaPlugin plugin;
    private final Storage storage;
    private final GroupService groups;
    private final NicknameColorService nicknameColors;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    public PermissionService(JavaPlugin plugin, Storage storage, GroupService groups, NicknameColorService nicknameColors) {
        this.plugin = plugin;
        this.storage = storage;
        this.groups = groups;
        this.nicknameColors = nicknameColors;
    }

    public UserData getUser(UUID uuid) {
        UserData user = storage.get(uuid);
        if (user != null && groups.get(user.group()) != null) return user;
        return user == null
                ? new UserData(uuid, "", groups.defaultGroup())
                : new UserData(uuid, user.name(), groups.defaultGroup(), user.nicknameColor());
    }

    public String getGroup(UUID uuid) { return getUser(uuid).group(); }
    public String getPrefix(UUID uuid) { return groups.prefix(getGroup(uuid)); }
    public String getNicknameColor(UUID uuid) { return nicknameColors.resolveColor(getUser(uuid), groups).toString(); }

    public boolean hasPermission(UUID uuid, String permission) {
        if (permission == null || permission.isBlank() || permission.length() > 128) return false;
        Player player = plugin.getServer().getPlayer(uuid);
        return player != null && player.hasPermission(permission);
    }

    public synchronized void setGroup(Player player, String group) {
        if (player == null || groups.get(group) == null) throw new IllegalArgumentException("grupo inválido");
        UserData current = getUser(player.getUniqueId());
        storage.put(new UserData(player.getUniqueId(), player.getName(), group, current.nicknameColor()));
    }

    public synchronized boolean setNicknameColor(Player player, String color) {
        if (player == null || color == null || color.isBlank() || !nicknameColors.allowedColors().containsKey(color.trim().toLowerCase(java.util.Locale.ROOT))) {
            return false;
        }
        UserData current = getUser(player.getUniqueId());
        storage.put(new UserData(player.getUniqueId(), player.getName(), current.group(), color));
        return true;
    }

    public synchronized void ensureUser(Player player) {
        UserData existing = storage.get(player.getUniqueId());
        if (existing == null || groups.get(existing.group()) == null) {
            storage.put(new UserData(player.getUniqueId(), player.getName(), groups.defaultGroup(), ""));
        } else if (!existing.name().equals(player.getName())) {
            storage.put(new UserData(existing.uuid(), player.getName(), existing.group(), existing.nicknameColor()));
        }
    }

    public synchronized void apply(Player player, UserData user) {
        remove(player);
        PermissionAttachment attachment = player.addAttachment(plugin);
        for (String permission : groups.resolvePermissions(user.group())) attachment.setPermission(permission, true);
        attachments.put(player.getUniqueId(), attachment);
        nicknameColors.apply(player, user, groups);
    }

    public synchronized void remove(Player player) {
        if (player == null) return;
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) player.removeAttachment(old);
        nicknameColors.remove(player);
    }

    public synchronized void clearAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) remove(player);
    }
}
