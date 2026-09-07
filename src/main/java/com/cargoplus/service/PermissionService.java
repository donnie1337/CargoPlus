package com.cargoplus.service;

import com.cargoplus.model.UserData;
import com.cargoplus.storage.Storage;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.permissions.PermissionAttachment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PermissionService {
    private final JavaPlugin plugin;
    private final Storage storage;
    private final GroupService groups;
    private final NicknameColorService nicknameColors;
    private final CargoPlusColorConfig chatColors;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    public PermissionService(JavaPlugin plugin, Storage storage, GroupService groups, NicknameColorService nicknameColors, CargoPlusColorConfig chatColors) {
        this.plugin = plugin;
        this.storage = storage;
        this.groups = groups;
        this.nicknameColors = nicknameColors;
        this.chatColors = chatColors;
    }

    public UserData getUser(UUID uuid) {
        UserData user = storage.get(uuid);
        if (user != null && groups.get(user.group()) != null) return user;
        return user == null
                ? new UserData(uuid, "", groups.defaultGroup())
                : new UserData(uuid, user.name(), groups.defaultGroup(), user.chatColor());
    }

    public String getGroup(UUID uuid) { return getUser(uuid).group(); }
    public String getPrefix(UUID uuid) { return groups.prefix(getGroup(uuid)); }
    public String getNicknameColor(UUID uuid) { return nicknameColors.resolveColor(getUser(uuid), groups).toString(); }

    public String getChatColor(UUID uuid) {
        String color = getUser(uuid).chatColor();
        ChatColor resolved = chatColors.resolve(color);
        if (resolved != null) return resolved.toString();
        ChatColor fallback = chatColors.resolve(chatColors.defaultColor());
        return fallback == null ? ChatColor.WHITE.toString() : fallback.toString();
    }

    public Map<String, String> getChatColors() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : chatColors.allowedColors().keySet()) {
            ChatColor color = chatColors.resolve(name);
            if (color != null) result.put(name, color.toString());
        }
        return result;
    }

    public String getDefaultChatColor() { return chatColors.defaultColor(); }

    public boolean hasPermission(UUID uuid, String permission) {
        if (permission == null || permission.isBlank() || permission.length() > 128) return false;
        Player player = plugin.getServer().getPlayer(uuid);
        return player != null && player.hasPermission(permission);
    }

    public synchronized void setGroup(Player player, String group) {
        if (player == null || groups.get(group) == null) throw new IllegalArgumentException("grupo inválido");
        UserData current = getUser(player.getUniqueId());
        storage.put(new UserData(player.getUniqueId(), player.getName(), group, current.chatColor()));
    }

    public synchronized boolean setChatColor(Player player, String color) {
        return player != null && setChatColor(player.getUniqueId(), color, player.getName());
    }

    public synchronized boolean setChatColor(UUID uuid, String color) {
        if (uuid == null) return false;
        UserData current = getUser(uuid);
        return setChatColor(uuid, color, current.name());
    }

    private boolean setChatColor(UUID uuid, String color, String playerName) {
        if (uuid == null || color == null || color.isBlank() || !chatColors.isAllowed(color)) return false;
        UserData current = getUser(uuid);
        String normalized = color.trim().toLowerCase(java.util.Locale.ROOT);
        storage.put(new UserData(uuid, playerName, current.group(), normalized));
        return true;
    }

    public synchronized void ensureUser(Player player) {
        UserData existing = storage.get(player.getUniqueId());
        if (existing == null || groups.get(existing.group()) == null) {
            storage.put(new UserData(player.getUniqueId(), player.getName(), groups.defaultGroup(), ""));
        } else if (!existing.name().equals(player.getName())) {
            storage.put(new UserData(existing.uuid(), player.getName(), existing.group(), existing.chatColor()));
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
