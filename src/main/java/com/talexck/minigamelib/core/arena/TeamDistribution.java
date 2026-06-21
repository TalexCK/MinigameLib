package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.arena.ArenaTeam;
import com.talexck.minigamelib.api.arena.ArenaTeamColor;
import com.talexck.minigamelib.api.arena.ArenaTeamSpawn;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Pure team-assignment logic, extracted from the arena controller so it can be unit tested without
 * a running server.
 */
final class TeamDistribution {

  private TeamDistribution() {
  }

  /**
   * Distributes players across teams using round-robin assignment.
   *
   * <p>The number of teams is bounded by the configured team colors (or all colors when none are
   * configured) and by the player count. When {@code maxTeamSize} is positive, the team count is
   * raised as needed so that no team exceeds that size, still capped by the number of available
   * colors.
   *
   * @param playerNames players to assign, in order
   * @param configuredColors team colors derived from the layout's team spawns; empty means "use all
   *     colors"
   * @param maxTeamSize maximum players per team, or {@code 0}/negative for unlimited
   * @return immutable teams with their assigned players
   */
  static List<ArenaTeam> resolveTeams(List<String> playerNames,
      List<ArenaTeamColor> configuredColors, int maxTeamSize) {
    if (playerNames.isEmpty()) {
      return List.of();
    }
    ArenaTeamColor[] colors = configuredColors.isEmpty() ? ArenaTeamColor.values()
        : configuredColors.toArray(ArenaTeamColor[]::new);
    int teamCount = Math.min(playerNames.size(), colors.length);
    if (maxTeamSize > 0) {
      int neededForCap = (playerNames.size() + maxTeamSize - 1) / maxTeamSize;
      teamCount = Math.min(colors.length, Math.max(teamCount, neededForCap));
    }
    List<ArenaTeam> teams = new ArrayList<>();
    for (int teamIndex = 0; teamIndex < teamCount; teamIndex++) {
      List<String> members = new ArrayList<>();
      for (int playerIndex = teamIndex; playerIndex < playerNames.size(); playerIndex += teamCount) {
        members.add(playerNames.get(playerIndex));
      }
      teams.add(new ArenaTeam(colors[teamIndex], members));
    }
    return teams;
  }

  /** Builds a per-color lookup of spawn points from a list of team spawns. */
  static Map<ArenaTeamColor, List<ArenaPoint>> teamSpawnMap(List<ArenaTeamSpawn> spawns) {
    Map<ArenaTeamColor, List<ArenaPoint>> map = new EnumMap<>(ArenaTeamColor.class);
    for (ArenaTeamSpawn spawn : spawns) {
      map.put(spawn.color(), spawn.spawnPoints());
    }
    return map;
  }
}
