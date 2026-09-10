package com.cargoplus.api;

import com.cargoplus.service.GroupService;
import com.cargoplus.service.PermissionService;
import com.cargoplus.service.PrefixAnimationService;

import java.util.Map;
import java.util.UUID;

public final class CargoPlusAPI {
    private final PermissionService permissions;
    private final GroupService groups;
    private final PrefixAnimationService prefixAnimation;

    public CargoPlusAPI(PermissionService permissions, GroupService groups, PrefixAnimationService prefixAnimation) {
        this.permissions = permissions;
        this.groups = groups;
        this.prefixAnimation = prefixAnimation;
    }

    public String getGroup(UUID uuid) { return permissions.getGroup(uuid); }
    public String getPrefix(UUID uuid) { return permissions.getPrefix(uuid); }
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
}
