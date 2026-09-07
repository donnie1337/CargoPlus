package com.cargoplus.model;

import java.util.Locale;
import java.util.UUID;

public record UserData(UUID uuid, String name, String group, String chatColor) {
    public UserData(UUID uuid, String name, String group) {
        this(uuid, name, group, "");
    }

    public UserData {
        if (uuid == null) throw new IllegalArgumentException("uuid");
        name = name == null ? "" : name;
        group = group == null || group.isBlank() ? "membro" : group.toLowerCase(Locale.ROOT);
        chatColor = chatColor == null ? "" : chatColor.trim().toLowerCase(Locale.ROOT);
    }
}
