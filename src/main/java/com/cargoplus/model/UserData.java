package com.cargoplus.model;

import java.util.UUID;

public record UserData(UUID uuid, String name, String group) {
    public UserData {
        if (uuid == null) throw new IllegalArgumentException("uuid");
        name = name == null ? "" : name;
        group = group == null || group.isBlank() ? "membro" : group.toLowerCase(java.util.Locale.ROOT);
    }
}
