package com.cargoplus.service;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class CargoPlusColorConfig {
    private final Map<String, String> colors = new LinkedHashMap<>();
    private final String defaultColor;

    public CargoPlusColorConfig(FileConfiguration config) {
        var section = config.getConfigurationSection("chat.colors");
        if (section != null) {
            for (String rawName : section.getKeys(false)) {
                String name = normalize(rawName);
                ChatColor color = parse(section.getString(rawName, ""));
                if (!name.isBlank() && color != null) colors.put(name, color.name());
            }
        }
        if (colors.isEmpty()) colors.put("branco", ChatColor.WHITE.name());
        String configuredDefault = normalize(config.getString("chat.default-color", "branco"));
        defaultColor = colors.containsKey(configuredDefault) ? configuredDefault : colors.keySet().iterator().next();
    }

    public String defaultColor() { return defaultColor; }
    public Map<String, String> allowedColors() { return Map.copyOf(colors); }
    public boolean isAllowed(String name) { return name != null && colors.containsKey(normalize(name)); }

    public ChatColor resolve(String name) {
        String stored = colors.get(normalize(name));
        if (stored == null) return null;
        try { return ChatColor.valueOf(stored); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }

    private static ChatColor parse(String value) {
        if (value == null) return null;
        String translated = ChatColor.translateAlternateColorCodes('&', value.trim());
        if (translated.length() != 2 || translated.charAt(0) != ChatColor.COLOR_CHAR) return null;
        return ChatColor.getByChar(translated.charAt(1));
    }
}
