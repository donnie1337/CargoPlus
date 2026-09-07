package com.cargoplus.service;

import com.cargoplus.model.UserData;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NicknameColorService {
    private static final String TEAM_PREFIX = "cp_";
    private final CargoPlusColorConfig colors;
    private final Map<UUID, String> teams = new ConcurrentHashMap<>();

    public NicknameColorService(CargoPlusColorConfig colors) {
        this.colors = colors;
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
        applyTeam(player, color);
    }

    public void remove(Player player) {
        if (player == null) return;
        String teamName = teams.remove(player.getUniqueId());
        if (teamName != null) {
            Scoreboard scoreboard = player.getScoreboard();
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                team.removeEntry(player.getName());
                if (team.getEntries().isEmpty()) team.unregister();
            }
        }
        player.setDisplayName(player.getName());
        player.setPlayerListName(player.getName());
    }

    private void applyTeam(Player player, ChatColor color) {
        Scoreboard scoreboard = player.getScoreboard();
        String teamName = TEAM_PREFIX + player.getUniqueId().toString().replace("-", "").substring(0, 13).toLowerCase(Locale.ROOT);
        Team current = scoreboard.getEntryTeam(player.getName());
        if (current != null && !current.getName().equals(teamName)) current.removeEntry(player.getName());
        Team team = scoreboard.getTeam(teamName);
        if (team == null) team = scoreboard.registerNewTeam(teamName);
        team.setColor(color);
        team.addEntry(player.getName());
        teams.put(player.getUniqueId(), teamName);
    }

    public Map<String, String> allowedColors() { return colors.allowedColors(); }
}
