package com.cargoplus.service;

import com.cargoplus.model.Group;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class GroupService {
    private static final int MAX_GROUPS = 64;
    private static final int MAX_PARENTS_PER_GROUP = 8;
    private static final int MAX_PERMISSIONS_PER_GROUP = 64;

    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final List<String> hierarchy = new ArrayList<>();
    private final Map<String, Set<String>> permissionCache = new ConcurrentHashMap<>();
    private final String defaultGroup;
    private final CargoPlusColorConfig colors;

    public GroupService(FileConfiguration config) { this(config, new CargoPlusColorConfig(config)); }
    public GroupService(FileConfiguration config, CargoPlusColorConfig colors) {
        this.colors = colors;
        this.defaultGroup = normalize(config.getString("default-group", "membro"));
        if (!isSafeGroupName(defaultGroup)) throw new IllegalStateException("default-group invalido: " + defaultGroup);
        for (String name : config.getStringList("hierarchy")) {
            String normalized = normalize(name);
            if (!isSafeGroupName(normalized)) throw new IllegalStateException("Cargo invalido na hierarquia: " + name);
            if (!hierarchy.add(normalized)) throw new IllegalStateException("Cargo duplicado na hierarquia: " + normalized);
        }
        if (hierarchy.isEmpty()) throw new IllegalStateException("hierarchy nao pode ser vazia.");
        if (!hierarchy.contains(defaultGroup)) throw new IllegalStateException("default-group fora da hierarquia: " + defaultGroup);
        var section = config.getConfigurationSection("groups");
        if (section == null) throw new IllegalStateException("Secao groups inexistente.");
        if (section.getKeys(false).size() > MAX_GROUPS) throw new IllegalStateException("Quantidade de cargos excede o limite de " + MAX_GROUPS + ".");
        for (String rawName : section.getKeys(false)) {
            String name = normalize(rawName);
            if (!isSafeGroupName(name)) throw new IllegalStateException("Nome de cargo invalido: " + rawName);
            if (groups.containsKey(name)) throw new IllegalStateException("Cargos duplicados apos normalizacao: " + name);
            List<String> parents = normalizeGroups(section.getStringList(rawName + ".parents"));
            if (parents.size() > MAX_PARENTS_PER_GROUP) throw new IllegalStateException("Cargo " + name + " possui pais demais (maximo " + MAX_PARENTS_PER_GROUP + ").");
            List<String> permissions = normalizePermissions(section.getStringList(rawName + ".permissions"));
            if (permissions.size() > MAX_PERMISSIONS_PER_GROUP) throw new IllegalStateException("Cargo " + name + " possui permissoes demais (maximo " + MAX_PERMISSIONS_PER_GROUP + ").");
            groups.put(name, new Group(name, section.getString(rawName + ".display-name", name), section.getString(rawName + ".prefix", ""), normalizeColorName(section.getString(rawName + ".name-color", "branco")), permissions, parents));
        }
        for (String group : hierarchy) if (!groups.containsKey(group)) throw new IllegalStateException("Cargo da hierarquia nao existe em groups: " + group);
        for (Group group : groups.values()) for (String parent : group.parents()) if (!groups.containsKey(parent)) throw new IllegalStateException("Cargo " + group.name() + " referencia pai inexistente: " + parent);
        validateParentCycles();
    }

    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static String normalizeColorName(String value) { String normalized = normalize(value); return normalized.isBlank() ? "branco" : normalized; }
    private static boolean isSafeGroupName(String value) { return value.matches("[a-z0-9_-]{1,32}"); }
    private static List<String> normalizeGroups(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) { String normalized = normalize(value); if (!isSafeGroupName(normalized)) throw new IllegalStateException("Nome de pai invalido: " + value); if (!result.add(normalized)) throw new IllegalStateException("Pai duplicado na configuracao: " + normalized); }
        return List.copyOf(result);
    }
    private static List<String> normalizePermissions(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) { String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT); if (normalized.isBlank()) continue; if (normalized.length() > 128 || !normalized.matches("[a-z0-9_.:*\\-]+")) throw new IllegalStateException("Permissao invalida: " + value); if (normalized.contains("*")) throw new IllegalStateException("Permissao excessivamente ampla nao permitida: " + value); if (!result.add(normalized)) throw new IllegalStateException("Permissao duplicada: " + normalized); }
        return List.copyOf(result);
    }
    private void validateParentCycles() { Map<String, VisitState> states = new HashMap<>(); for (String group : groups.keySet()) states.put(group, VisitState.UNVISITED); for (String group : groups.keySet()) detectCycle(group, states, new ArrayDeque<>()); }
    private void detectCycle(String name, Map<String, VisitState> states, Deque<String> path) { VisitState state = states.get(name); if (state == VisitState.VISITING) { path.addLast(name); throw new IllegalStateException("Ciclo detectado na hierarquia de cargos: " + String.join(" -> ", path)); } if (state == VisitState.VISITED) return; states.put(name, VisitState.VISITING); path.addLast(name); for (String parent : groups.get(name).parents()) detectCycle(parent, states, path); path.removeLast(); states.put(name, VisitState.VISITED); }
    private enum VisitState { UNVISITED, VISITING, VISITED }
    public String defaultGroup() { return defaultGroup; }
    public Group get(String name) { return groups.get(normalize(name)); }
    public Collection<Group> all() { return List.copyOf(groups.values()); }
    public List<String> hierarchy() { return List.copyOf(hierarchy); }
    public int indexOf(String group) { return hierarchy.indexOf(normalize(group)); }
    public String next(String current) { int index = indexOf(current); return index >= 0 && index + 1 < hierarchy.size() && groups.containsKey(hierarchy.get(index + 1)) ? hierarchy.get(index + 1) : null; }
    public String previous(String current) { int index = indexOf(current); return index > 0 && groups.containsKey(hierarchy.get(index - 1)) ? hierarchy.get(index - 1) : null; }
    public Set<String> resolvePermissions(String group) { String normalized = normalize(group); if (normalized.isBlank()) return Set.of(); return permissionCache.computeIfAbsent(normalized, key -> { Set<String> result = new LinkedHashSet<>(); resolve(key, result, new HashSet<>()); return Set.copyOf(result); }); }
    private void resolve(String name, Set<String> result, Set<String> visiting) { name = normalize(name); if (!visiting.add(name)) return; Group group = groups.get(name); if (group == null) return; for (String parent : group.parents()) resolve(parent, result, visiting); result.addAll(group.permissions()); visiting.remove(name); }

    /** Retorna o prefixo exatamente como configurado, preservando gradientes e estilos. */
    public String prefix(String group) {
        Group g = get(group);
        if (g == null || g.prefix().isBlank()) return "";
        return ChatColor.translateAlternateColorCodes('&', g.prefix());
    }

    public String nameColor(String group) { Group g = get(group); return g == null ? "branco" : g.nameColor(); }
}
