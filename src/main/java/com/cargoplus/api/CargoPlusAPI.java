package com.cargoplus.api;

import com.cargoplus.service.GroupService;
import com.cargoplus.service.PermissionService;
import com.cargoplus.service.PrefixAnimationService;
import com.cargoplus.service.NicknameColorService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

import java.util.Map;
import java.util.UUID;

public final class CargoPlusAPI {
    private final PermissionService permissions;
    private final GroupService groups;
    private final PrefixAnimationService prefixAnimation;
    private final NicknameColorService nicknameColors;

    public CargoPlusAPI(PermissionService permissions, GroupService groups, PrefixAnimationService prefixAnimation, NicknameColorService nicknameColors) {
        this.permissions = permissions;
        this.groups = groups;
        this.prefixAnimation = prefixAnimation;
        this.nicknameColors = nicknameColors;
    }

    public String getGroup(UUID uuid) { return permissions.getGroup(uuid); }
    /** Retorna o prefixo estático para integrações que não devem usar animação. */
    public String getPrefix(UUID uuid) {
        String group = getGroup(uuid);
        if (group == null || group.isBlank()) return "";
        var cargo = groups.get(group);
        if (cargo == null || cargo.prefix().isBlank()) return "";
        return ChatColor.translateAlternateColorCodes('&', cargo.prefix());
    }
    public String getAnimatedPrefix(UUID uuid) {
        String group = getGroup(uuid);
        if (group == null || group.isBlank()) return "";
        var cargo = groups.get(group);
        if (cargo == null) return "";
        return prefixAnimation.animate(cargo.prefix(), group);
    }
    public String getNicknameColor(UUID uuid) { return permissions.getNicknameColor(uuid); }
    public String getChatColor(UUID uuid) { return permissions.getChatColor(uuid); }
    public boolean setChatColor(UUID uuid, String color) {
        return permissions.setChatColor(uuid, color);
    }
    public boolean hasPermission(UUID uuid, String permission) { return permissions.hasPermission(uuid, permission); }
    public boolean hasCargoPermission(UUID uuid, String permission) { return permissions.hasCargoPermission(uuid, permission); }
    public Map<String, String> getChatColors() { return permissions.getChatColors(); }
    public String getDefaultChatColor() { return permissions.getDefaultChatColor(); }
    public GroupService groups() { return groups; }
    /** Define o sufixo visual da nametag customizada acima da cabeça. */
    public void setNametagSuffix(UUID uuid, String suffix) {
        if (uuid == null || nicknameColors == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;
        String group = getGroup(uuid);
        nicknameColors.setNametagSuffix(player, suffix, group, groups);
    }
}
