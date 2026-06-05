package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaHandle;
import com.talexck.minigamelib.api.arena.ArenaLayout;
import com.talexck.minigamelib.api.arena.ArenaLifecycleListener;
import com.talexck.minigamelib.api.arena.ArenaPlayerStats;
import com.talexck.minigamelib.api.arena.ArenaSettings;
import com.talexck.minigamelib.api.arena.ArenaStatus;
import com.talexck.minigamelib.api.arena.ArenaTeam;
import com.talexck.minigamelib.api.arena.ArenaTeamColor;
import com.talexck.minigamelib.api.arena.ArenaTeamStats;
import com.talexck.minigamelib.core.world.RuntimeWorld;
import org.bukkit.boss.BossBar;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class RuntimeArena {

  private final String arenaId;
  private final String templateId;
  private final RuntimeWorld world;
  private final ArenaLayout layout;
  private final ArenaSettings settings;
  private final List<ArenaTeam> teams;
  private final List<String> playerNames;
  private final Map<String, PlayerRuntimeStats> playerStats = new HashMap<>();
  private final Map<ArenaTeamColor, Boolean> teamFailures = new EnumMap<>(ArenaTeamColor.class);
  private final List<BukkitTask> boundaryTasks = new ArrayList<>();
  private final ArenaLifecycleListener listener;
  private volatile ArenaStatus status = ArenaStatus.CREATED;
  private ArenaTeamColor winningTeam;
  private BossBar bossBar;

  RuntimeArena(String arenaId, String templateId, RuntimeWorld world, ArenaLayout layout,
      ArenaSettings settings, List<ArenaTeam> teams, ArenaLifecycleListener listener) {
    this.arenaId = arenaId;
    this.templateId = templateId;
    this.world = world;
    this.layout = layout;
    this.settings = settings;
    this.teams = List.copyOf(teams);
    this.playerNames = teams.stream().flatMap(team -> team.playerNames().stream()).toList();
    this.listener = listener;
    initializeStats();
  }

  String arenaId() {
    return arenaId;
  }

  String templateId() {
    return templateId;
  }

  RuntimeWorld world() {
    return world;
  }

  ArenaLayout layout() {
    return layout;
  }

  ArenaSettings settings() {
    return settings;
  }

  List<String> playerNames() {
    return playerNames;
  }

  List<ArenaTeam> teams() {
    return teams;
  }

  ArenaLifecycleListener listener() {
    return listener;
  }

  ArenaStatus status() {
    return status;
  }

  void setStatus(ArenaStatus status) {
    this.status = status;
  }

  Optional<ArenaTeamColor> teamOf(String playerName) {
    PlayerRuntimeStats stats = playerStats.get(playerName);
    return stats == null ? Optional.empty() : Optional.of(stats.teamColor());
  }

  void recordKill(String killerName) {
    PlayerRuntimeStats stats = playerStats.get(killerName);
    if (stats != null) {
      stats.addKill();
    }
  }

  void recordDeath(String playerName) {
    PlayerRuntimeStats stats = playerStats.get(playerName);
    if (stats != null) {
      stats.addDeath();
      stats.setFailed(true);
      updateTeamFailure(stats.teamColor());
    }
  }

  boolean isFailed(String playerName) {
    PlayerRuntimeStats stats = playerStats.get(playerName);
    return stats != null && stats.failed();
  }

  boolean isTeamFailed(ArenaTeamColor color) {
    return teamFailures.getOrDefault(color, false);
  }

  Optional<ArenaTeamColor> singleAliveTeam() {
    List<ArenaTeamColor> aliveTeams = teams.stream()
        .map(ArenaTeam::color)
        .filter(color -> !isTeamFailed(color))
        .toList();
    return aliveTeams.size() == 1 ? Optional.of(aliveTeams.getFirst()) : Optional.empty();
  }

  void setWinningTeam(ArenaTeamColor winningTeam) {
    this.winningTeam = winningTeam;
  }

  ArenaTeamColor winningTeam() {
    return winningTeam;
  }

  List<ArenaPlayerStats> playerStats() {
    return playerStats.values().stream()
        .map(PlayerRuntimeStats::snapshot)
        .toList();
  }

  List<ArenaTeamStats> teamStats() {
    return teams.stream().map(team -> {
      int kills = team.playerNames().stream()
          .map(playerStats::get)
          .filter(stats -> stats != null)
          .mapToInt(PlayerRuntimeStats::kills)
          .sum();
      int deaths = team.playerNames().stream()
          .map(playerStats::get)
          .filter(stats -> stats != null)
          .mapToInt(PlayerRuntimeStats::deaths)
          .sum();
      return new ArenaTeamStats(team.color(), team.playerNames(), kills, deaths,
          isTeamFailed(team.color()));
    }).toList();
  }

  List<BukkitTask> boundaryTasks() {
    return boundaryTasks;
  }

  BossBar bossBar() {
    return bossBar;
  }

  void setBossBar(BossBar bossBar) {
    this.bossBar = bossBar;
  }

  ArenaHandle handle() {
    return new ArenaHandle(arenaId, templateId, world.runtimeWorldName(), status, layout, settings,
        playerNames, teams);
  }

  private void initializeStats() {
    for (ArenaTeam team : teams) {
      teamFailures.put(team.color(), team.playerNames().isEmpty());
      for (String playerName : team.playerNames()) {
        playerStats.put(playerName, new PlayerRuntimeStats(playerName, team.color()));
      }
    }
  }

  private void updateTeamFailure(ArenaTeamColor color) {
    boolean failed = teams.stream()
        .filter(team -> team.color() == color)
        .findFirst()
        .map(team -> team.playerNames().stream().allMatch(this::isFailed))
        .orElse(true);
    teamFailures.put(color, failed);
  }

  private static final class PlayerRuntimeStats {

    private final String playerName;
    private final ArenaTeamColor teamColor;
    private int kills;
    private int deaths;
    private boolean failed;

    private PlayerRuntimeStats(String playerName, ArenaTeamColor teamColor) {
      this.playerName = playerName;
      this.teamColor = teamColor;
    }

    private ArenaTeamColor teamColor() {
      return teamColor;
    }

    private int kills() {
      return kills;
    }

    private int deaths() {
      return deaths;
    }

    private boolean failed() {
      return failed;
    }

    private void addKill() {
      kills++;
    }

    private void addDeath() {
      deaths++;
    }

    private void setFailed(boolean failed) {
      this.failed = failed;
    }

    private ArenaPlayerStats snapshot() {
      return new ArenaPlayerStats(playerName, teamColor, kills, deaths, failed);
    }
  }
}
