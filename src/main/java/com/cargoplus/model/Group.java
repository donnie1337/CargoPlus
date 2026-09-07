package com.cargoplus.model;

import java.util.List;

public record Group(String name, String displayName, String prefix, String nameColor, List<String> permissions, List<String> parents) {
    public Group {
        name = name == null ? "" : name;
        displayName = displayName == null ? name : displayName;
        prefix = prefix == null ? "" : prefix;
        nameColor = nameColor == null || nameColor.isBlank() ? "branco" : nameColor.trim().toLowerCase(java.util.Locale.ROOT);
        permissions = List.copyOf(permissions == null ? List.of() : permissions);
        parents = List.copyOf(parents == null ? List.of() : parents);
    }
}
