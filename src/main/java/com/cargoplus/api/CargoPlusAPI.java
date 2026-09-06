package com.cargoplus.api;

import com.cargoplus.service.GroupService;
import com.cargoplus.service.PermissionService;

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
    public boolean hasPermission(UUID uuid, String permission) { return permissions.hasPermission(uuid, permission); }
    public GroupService groups() { return groups; }
}
