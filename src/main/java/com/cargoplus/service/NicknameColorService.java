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
        player.setPlayerListName(color + player.getName());
        applyTeam(player, color, user.group(), groups);
    }

    public void refreshAnimatedPrefix(Player player, GroupService groups) {
        if (player == null || !player.isOnline() || groups == null) return;
        String group = groups.get(playerGroup(player)) == null ? "" : playerGroup(player);
        if (group.isBlank()) return;
        Team team = findTeam(player);
        if (team == null) return;
        String configuredPrefix = groups.get(group).prefix();
        String renderedPrefix = prefixAnimation.animate(configuredPrefix, group);
        writePrefix(team, renderedPrefix);
    }

    public void remove(Player player) {
        if (player == null) return;
        String teamName = teams.remove(player.getUniqueId());
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

        // The same scoreboard Team controls the nametag above the player and
        // the player's entry in TAB. We therefore animate this one prefix so
        // both views stay perfectly synchronized.
        String configuredPrefix = groups.get(group) == null ? "" : groups.get(group).prefix();
        writePrefix(team, prefixAnimation.animate(configuredPrefix, group));

        player.setCollidable(false);
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
        if (writePaperPrefix(team, safe)) return;
        team.setPrefix(safe);
    }

    private boolean writePaperPrefix(Team team, String legacyPrefix) {
        // Prefer the legacy setter because CargoPlus is compiled against the
        // Bukkit API and the generated RGB §x format is supported by Paper.
        // This method exists only as a compatibility fallback for runtimes
        // exposing a Component-only Team prefix API.
        try {
            Method method = team.getClass().getMethod("setPrefix", String.class);
            method.invoke(team, legacyPrefix);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private Team findTeam(Player player) {
        Scoreboard scoreboard = player.getScoreboard();
        Team team = scoreboard.getEntryTeam(player.getName());
        if (team != null) return team;
        Scoreboard main = org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard();
        return main == scoreboard ? null : main.getEntryTeam(player.getName());
    }

    private String playerGroup(Player player) {
        return player.getScoreboard().getEntryTeam(player.getName()) == null ? "" : "dev";
    }

    public Map<String, String> allowedColors() { return colors.allowedColors(); }
}
