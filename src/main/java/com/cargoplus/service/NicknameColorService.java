package com.cargoplus.service;

import com.cargoplus.model.UserData;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class NicknameColorService {
    private static final String TEAM_PREFIX = "cp_";
    private final CargoPlusColorConfig colors;
    private final Map<UUID, String> teams = new LinkedHashMap<>();

    public NicknameColorService(CargoPlusColorConfig colors) {
        this.colors = colors;
    }

    public String colorName(UserData user, GroupService groups) {
        String configured = user.nicknameColor();
        if (!configured.isBlank() && colors.isAllowed(configured)) return configured;
        String groupColor = groups.nameColor(user.group());
        return colors.isAllowed(groupColor) ? groupColor : colors.defaultColor();
    }

    public void apply(Player player, UserData user, GroupService groups) {
        if (player == null || !player.isOnline()) return;
        String colorName = colorName(user, groups);
        ChatColor color = colors.resolve(colorName);
        if (color == null) color = colors.resolve(colors.defaultColor());
        if (color == null) color = ChatColor.WHITE;

        player.setDisplayName(color + player.getName());
        player.setPlayerListName(color + player.getName());
        applyTeam(player, color);
    }

    public void remove(Player player) {
        if (player == null) return;
        String teamName = teams.remove(player.getUniqueId());
        if (teamName != null) {
            Team team = player.getScoreboard().getTeam(teamName);
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

    public Map<String, String> allowedColors() {
        return colors.allowedColors();
    }
}
