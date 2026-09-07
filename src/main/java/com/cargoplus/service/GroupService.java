package com.cargoplus.service;

import com.cargoplus.model.Group;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public final class GroupService {
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final List<String> hierarchy = new ArrayList<>();
    private final String defaultGroup;

    public GroupService(FileConfiguration config) {
        this.defaultGroup = normalize(config.getString("default-group", "membro"));
        for (String name : config.getStringList("hierarchy")) {
            String normalized = normalize(name);
            if (!normalized.isBlank() && !hierarchy.contains(normalized)) hierarchy.add(normalized);
        }
        var section = config.getConfigurationSection("groups");
        if (section != null) {
            for (String rawName : section.getKeys(false)) {
                String name = normalize(rawName);
                if (!isSafeGroupName(name)) continue;
                groups.put(name, new Group(
                        name,
                        section.getString(rawName + ".display-name", name),
                        section.getString(rawName + ".prefix", ""),
                        normalizeColorName(section.getString(rawName + ".name-color", "branco")),
                        normalizePermissions(section.getStringList(rawName + ".permissions")),
                        normalizeGroups(section.getStringList(rawName + ".parents"))));
            }
        }
        if (!groups.containsKey(defaultGroup)) throw new IllegalStateException("default-group inexistente: " + defaultGroup);
    }

    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }

    private static String normalizeColorName(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? "branco" : normalized;
    }

    private static boolean isSafeGroupName(String value) { return value.matches("[a-z0-9_-]{1,32}"); }

    private static List<String> normalizeGroups(List<String> values) {
        return values.stream().map(GroupService::normalize).filter(GroupService::isSafeGroupName).distinct().toList();
    }

    private static List<String> normalizePermissions(List<String> values) {
        return values.stream().map(String::trim).filter(v -> !v.isBlank() && v.length() <= 128 && v.matches("[A-Za-z0-9_.:*\\-]+"))
                .map(v -> v.toLowerCase(Locale.ROOT)).distinct().toList();
    }

    public String defaultGroup() { return defaultGroup; }
    public Group get(String name) { return groups.get(normalize(name)); }
    public Collection<Group> all() { return List.copyOf(groups.values()); }
    public List<String> hierarchy() { return List.copyOf(hierarchy); }

    public int indexOf(String group) { return hierarchy.indexOf(normalize(group)); }

    public String next(String current) {
        int index = indexOf(current);
        return index >= 0 && index + 1 < hierarchy.size() && groups.containsKey(hierarchy.get(index + 1)) ? hierarchy.get(index + 1) : null;
    }

    public String previous(String current) {
        int index = indexOf(current);
        return index > 0 && groups.containsKey(hierarchy.get(index - 1)) ? hierarchy.get(index - 1) : null;
    }

    public Set<String> resolvePermissions(String group) {
        Set<String> result = new LinkedHashSet<>();
        resolve(group, result, new HashSet<>());
        return result;
    }

    private void resolve(String name, Set<String> result, Set<String> visiting) {
        name = normalize(name);
        if (!visiting.add(name)) return;
        Group group = groups.get(name);
        if (group == null) return;
        for (String parent : group.parents()) resolve(parent, result, visiting);
        result.addAll(group.permissions());
        visiting.remove(name);
    }

    public String prefix(String group) {
        Group g = get(group);
        return g == null ? "" : ChatColor.translateAlternateColorCodes('&', g.prefix());
    }

    public String nameColor(String group) {
        Group g = get(group);
        return g == null ? "branco" : g.nameColor();
    }
}
