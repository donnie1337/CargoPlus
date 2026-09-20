package com.cargoplus.service;

import com.cargoplus.model.UserData;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NicknameColorService {
    private static final String TEAM_PREFIX = "cp";
    private final CargoPlusColorConfig colors;
    private final PrefixAnimationService prefixAnimation;
    private final Map<UUID, String> teams = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastRenderedPrefixes = new ConcurrentHashMap<>();
    private final Map<UUID, Object> preservedSuffixes = new ConcurrentHashMap<>();

    public NicknameColorService(CargoPlusColorConfig colors, PrefixAnimationService prefixAnimation) {
        this.colors = colors;
        this.prefixAnimation = prefixAnimation;
    }

    public ChatColor resolveColor(UserData user, GroupService groups) {
        return user == null ? ChatColor.WHITE : resolveColor(groups, user.group());
    }

    /** Retorna a cor exata do nickname em RGB, sem conversão para legacy. */
    public String resolveRgbColor(UserData user, GroupService groups) {
        return resolveRgbColorInternal(user, groups);
    }

    public void apply(Player player, UserData user, GroupService groups) {
        if (player == null || !player.isOnline()) return;
        String rgbColor = resolveRgbColorInternal(user, groups);
        ChatColor legacyColor = resolveLegacyColor(rgbColor);
        player.setDisplayName(rgbColor + player.getName());
        applyTeam(player, legacyColor, user.group(), groups);
        refreshTabName(player, rgbColor);
    }

    public void refreshAnimatedPrefix(Player player, String group, GroupService groups) {
        if (player == null || !player.isOnline() || groups == null || group == null) return;
        var cargo = groups.get(group);
        if (cargo == null) return;

        Team team = ensureTeam(player, resolveColor(groups, group), group, groups);
        if (team == null) return;

        String animatedPrefix = prefixAnimation.animate(cargo.prefix(), group);
        String safePrefix = animatedPrefix == null ? "" : ChatColor.translateAlternateColorCodes('&', animatedPrefix);
        String previousPrefix = lastRenderedPrefixes.put(player.getUniqueId(), safePrefix);

        // Só envia uma atualização ao cliente quando o frame realmente mudou.
        // Reescrever o Team a cada 2 ticks fazia a tag do TAB piscar.
        if (!safePrefix.equals(previousPrefix) || !team.hasEntry(player.getName())) {
            if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
            team.setPrefix(safePrefix);
        }
    }

    public void remove(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        lastRenderedPrefixes.remove(uuid);
        String teamName = teams.remove(uuid);
        if (teamName != null) {
            Scoreboard scoreboard = player.getScoreboard();
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                preserveSuffix(player, team);
                team.removeEntry(player.getName());
                if (team.getEntries().isEmpty()) team.unregister();
            }
        }
        player.setDisplayName(player.getName());
        player.setPlayerListName(player.getName());
    }

    private void applyTeam(Player player, ChatColor color, String group, GroupService groups) {
        Team team = ensureTeam(player, color, group, groups);
        if (team == null) return;
        String prefix = prefixAnimation.animate(groups.get(group).prefix(), group);
        String safePrefix = prefix == null ? "" : ChatColor.translateAlternateColorCodes('&', prefix);
        lastRenderedPrefixes.put(player.getUniqueId(), safePrefix);
        team.setPrefix(safePrefix);
        player.setCollidable(false);
    }

    private Team ensureTeam(Player player, ChatColor color, String group, GroupService groups) {
        if (player == null || !player.isOnline() || groups == null || group == null) return null;
        Scoreboard scoreboard = player.getScoreboard();
        int hierarchyIndex = groups.indexOf(group);
        int sortIndex = hierarchyIndex >= 0 ? groups.hierarchy().size() - 1 - hierarchyIndex : 99;

        String uuidPart = player.getUniqueId().toString().replace("-", "");
        String teamName = TEAM_PREFIX + String.format(Locale.ROOT, "%02d", sortIndex) + uuidPart.substring(0, 12);

        String previousTeamName = teams.put(player.getUniqueId(), teamName);
        Team current = scoreboard.getEntryTeam(player.getName());
        if (current != null && !current.getName().equals(teamName)) {
            preserveSuffix(player, current);
            current.removeEntry(player.getName());
            if (current.getEntries().isEmpty()) current.unregister();
        }

        if (previousTeamName != null && !previousTeamName.equals(teamName)) {
            Team previous = scoreboard.getTeam(previousTeamName);
            if (previous != null && previous.getEntries().isEmpty()) {
                preserveSuffix(player, previous);
                previous.unregister();
            }
        }

        Team team = scoreboard.getTeam(teamName);
        if (team == null) team = scoreboard.registerNewTeam(teamName);
        team.setColor(color);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

        Object preservedSuffix = preservedSuffixes.remove(player.getUniqueId());
        if (preservedSuffix != null) restoreSuffix(team, preservedSuffix);
        if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
        player.setCollidable(false);
        return team;
    }

    private void refreshTabName(Player player, String color) {
        if (player == null || !player.isOnline()) return;
        player.setPlayerListName(color + player.getName());
    }

    private ChatColor resolveColor(GroupService groups, String group) {
        return resolveLegacyColor(resolveRgbColor(groups, group));
    }

    private String resolveRgbColorInternal(UserData user, GroupService groups) {
        return user == null ? "§f" : resolveRgbColor(groups, user.group());
    }

    private String resolveRgbColor(GroupService groups, String group) {
        if (groups == null || group == null) return "§f";
        var cargo = groups.get(group);
        if (cargo == null) return "§f";

        String configuredRgb = parseRgbAlias(cargo.nameColor());
        if (configuredRgb != null) return configuredRgb;

        String prefix = cargo.prefix();
        if (prefix == null || prefix.isBlank()) return "§f";

        var matcher = java.util.regex.Pattern
                .compile("<gradient:(#[0-9a-fA-F]{6}):(#[0-9a-fA-F]{6})>")
                .matcher(prefix);
        if (!matcher.find()) return "§f";
        return toSectionSignHex(matcher.group(2));
    }

    private String parseRgbAlias(String value) {
        if (value == null) return null;
        var matcher = java.util.regex.Pattern.compile("<cor:(#[0-9a-fA-F]{6})>").matcher(value.trim());
        return matcher.matches() ? toSectionSignHex(matcher.group(1)) : null;
    }

    private String toSectionSignHex(String hex) {
        String clean = hex.substring(1).toUpperCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder("§x");
        for (char c : clean.toCharArray()) builder.append('§').append(c);
        return builder.toString();
    }

    private ChatColor resolveLegacyColor(String rgbColor) {
        if (rgbColor == null || rgbColor.isBlank()) return ChatColor.WHITE;
        String hex = rgbColor.replace("§x", "").replace("§", "");
        if (hex.length() != 6) return ChatColor.WHITE;
        try {
            return nearestLegacyColor(Integer.parseInt(hex, 16));
        } catch (NumberFormatException ignored) {
            return ChatColor.WHITE;
        }
    }

    private ChatColor nearestLegacyColor(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        ChatColor[] palette = { ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
                ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY, ChatColor.DARK_GRAY,
                ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA, ChatColor.RED, ChatColor.LIGHT_PURPLE,
                ChatColor.YELLOW, ChatColor.WHITE };
        int[] values = { 0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
                0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF };
        int bestDistance = Integer.MAX_VALUE;
        ChatColor best = ChatColor.WHITE;
        for (int i = 0; i < palette.length; i++) {
            int pr = (values[i] >> 16) & 0xFF, pg = (values[i] >> 8) & 0xFF, pb = values[i] & 0xFF;
            int dr = r - pr, dg = g - pg, db = b - pb;
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) { bestDistance = distance; best = palette[i]; }
        }
        return best;
    }

    private void preserveSuffix(Player player, Team team) {
        if (player == null || team == null) return;
        Object suffix = readPaperSuffix(team);
        if (suffix != null) {
            preservedSuffixes.put(player.getUniqueId(), suffix);
            return;
        }
        String legacySuffix = team.getSuffix();
        if (legacySuffix != null && !legacySuffix.isEmpty()) preservedSuffixes.put(player.getUniqueId(), legacySuffix);
    }

    private Object readPaperSuffix(Team team) {
        try {
            Method method = team.getClass().getMethod("suffix");
            return method.invoke(team);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private void restoreSuffix(Team team, Object suffix) {
        if (suffix instanceof String legacySuffix) {
            team.setSuffix(legacySuffix);
            return;
        }
        try {
            for (Method method : team.getClass().getMethods()) {
                if (!method.getName().equals("suffix") || method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isInstance(suffix)) continue;
                method.invoke(team, suffix);
                return;
            }
        } catch (ReflectiveOperationException ignored) {
            // Keep CargoPlus compatible with the Spigot API if Paper's
            // Adventure suffix methods are not present at runtime.
        }
    }

    private void writePrefix(Team team, String legacyPrefix) {
        if (team == null) return;
        String safe = legacyPrefix == null ? "" : ChatColor.translateAlternateColorCodes('&', legacyPrefix);
        team.setPrefix(safe);
    }

    private Team findTeam(Player player) {
        Scoreboard scoreboard = player.getScoreboard();
        Team team = scoreboard.getEntryTeam(player.getName());
        if (team != null) return team;
        Scoreboard main = org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard();
        return main == scoreboard ? null : main.getEntryTeam(player.getName());
    }

    public Map<String, String> allowedColors() { return colors.allowedColors(); }
}
