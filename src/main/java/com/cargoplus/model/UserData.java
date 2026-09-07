package com.cargoplus.model;

import java.util.Locale;
import java.util.UUID;

public record UserData(UUID uuid, String name, String group, String nicknameColor) {
    public UserData(UUID uuid, String name, String group) {
        this(uuid, name, group, "");
    }

    public UserData {
        if (uuid == null) throw new IllegalArgumentException("uuid");
        name = name == null ? "" : name;
        group = group == null || group.isBlank() ? "membro" : group.toLowerCase(Locale.ROOT);
        nicknameColor = nicknameColor == null ? "" : nicknameColor.trim().toLowerCase(Locale.ROOT);
    }
}
