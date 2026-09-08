package com.cargoplus.service;

import com.cargoplus.model.Group;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class GroupService {
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final List<String> hierarchy = new ArrayList<>();
    private final Map<String, Set<String>> permissionCache = new ConcurrentHashMap<>();
    private final String defaultGroup;
    private final CargoPlusColorConfig colors;

    public GroupService(FileConfiguration config) {
        this(config, new CargoPlusColorConfig(config));
    }

    public GroupService(FileConfiguration config, CargoPlusColorConfig colors) {
        this.colors = colors;
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
        String normalized = normalize(group);
        if (normalized.isBlank()) return Set.of();
        return permissionCache.computeIfAbsent(normalized, key -> {
            Set<String> result = new LinkedHashSet<>();
            resolve(key, result, new HashSet<>());
            return Set.copyOf(result);
        });
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

    /** Formata o prefixo usando a cor oficial do cargo, preservando os estilos configurados. */
    public String prefix(String group) {
        Group g = get(group);
        if (g == null || g.prefix().isBlank()) return "";

        String prefix = ChatColor.translateAlternateColorCodes('&', g.prefix());
        String withoutColors = stripColorsOnly(prefix);
        ChatColor color = colors.resolve(g.nameColor());
        return color == null ? withoutColors : color + withoutColors;
    }

    /** Remove somente codigos de cor, preservando estilos como negrito (&l). */
    private static String stripColorsOnly(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.replaceAll("§(?:[0-9a-fA-F]|#[0-9a-fA-F]{6}|x(?:§[0-9a-fA-F]){6})", "");
    }

    public String nameColor(String group) {
        Group g = get(group);
        return g == null ? "branco" : g.nameColor();
    }
}
