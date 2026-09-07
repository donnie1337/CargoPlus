package com.cargoplus.api;

import com.cargoplus.service.GroupService;
import com.cargoplus.service.PermissionService;

import java.util.Map;
import java.util.UUID;

public final class CargoPlusAPI {
    private final PermissionService permissions;
    private final GroupService groups;

    public CargoPlusAPI(PermissionService permissions, GroupService groups) {
        this.permissions = permissions;
        this.groups = groups;
    }

    public String getGroup(UUID uuid) { return permissions.getGroup(uuid); }
    public String getPrefix(UUID uuid) { return permissions.getPrefix(uuid); }
    public String getNicknameColor(UUID uuid) { return permissions.getNicknameColor(uuid); }
    public String getChatColor(UUID uuid) { return permissions.getChatColor(uuid); }
    public boolean setChatColor(UUID uuid, String color) {
        return permissions.setChatColor(uuid, color);
    }
    public boolean hasPermission(UUID uuid, String permission) { return permissions.hasPermission(uuid, permission); }
    public Map<String, String> getChatColors() { return permissions.getChatColors(); }
    public String getDefaultChatColor() { return permissions.getDefaultChatColor(); }
    public GroupService groups() { return groups; }
}
