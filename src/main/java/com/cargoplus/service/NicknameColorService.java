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
        String groupColor = groups.nameColor(user.group());
        ChatColor color = colors.resolve(groupColor);
        return color == null ? ChatColor.WHITE : color;
    }

    public void apply(Player player, UserData user, GroupService groups) {
        if (player == null || !player.isOnline()) return;
        ChatColor color = resolveColor(user, groups);
        player.setDisplayName(color + player.getName());
        applyTeam(player, color, user.group(), groups);
        refreshTabName(player, color);
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

    private void refreshTabName(Player player, ChatColor color) {
        if (player == null || !player.isOnline()) return;
        player.setPlayerListName(color + player.getName());
    }

    private ChatColor resolveColor(GroupService groups, String group) {
        ChatColor color = colors.resolve(groups.nameColor(group));
        return color == null ? ChatColor.WHITE : color;
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
