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
  private final List<ArenaTeamColor> teamFailureOrder = new ArrayList<>();
  private final List<BukkitTask> boundaryTasks = new ArrayList<>();
  private final ArenaLifecycleListener listener;
  private volatile ArenaStatus status = ArenaStatus.CREATED;
  private long gameStartedAtMillis;
  private ArenaTeamColor winningTeam;
  private BossBar bossBar;
  private String tabScoreboardName;
  private String tabBossBarName;
  private int tabLayoutRevision;

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

  long aliveTeamCount() {
    return teams.stream()
        .map(ArenaTeam::color)
        .filter(color -> !isTeamFailed(color))
        .count();
  }

  void setWinningTeam(ArenaTeamColor winningTeam) {
    this.winningTeam = winningTeam;
  }

  ArenaTeamColor winningTeam() {
    return winningTeam;
  }

  void markGameStarted(long nowMillis) {
    this.gameStartedAtMillis = nowMillis;
  }

  long gameStartedAtMillis() {
    return gameStartedAtMillis;
  }

  long playerSurvivalSeconds(String playerName, long nowMillis) {
    PlayerRuntimeStats stats = playerStats.get(playerName);
    if (stats == null || gameStartedAtMillis <= 0L) {
      return 0L;
    }
    long end = stats.failedAtMillis() > 0L ? stats.failedAtMillis() : nowMillis;
    return Math.max(0L, (end - gameStartedAtMillis) / 1000L);
  }

  long teamSurvivalSeconds(ArenaTeam team, long nowMillis) {
    return team.playerNames().stream()
        .mapToLong(playerName -> playerSurvivalSeconds(playerName, nowMillis))
        .sum();
  }

  int teamScore(ArenaTeam team, long nowMillis) {
    return teamKills(team) * 20 + Math.toIntExact(Math.min(Integer.MAX_VALUE,
        teamSurvivalSeconds(team, nowMillis)));
  }

  int teamKills(ArenaTeam team) {
    return team.playerNames().stream()
        .map(playerStats::get)
        .filter(stats -> stats != null)
        .mapToInt(PlayerRuntimeStats::kills)
        .sum();
  }

  List<ArenaTeamColor> finalTeamRanking() {
    List<ArenaTeamColor> ranking = new ArrayList<>();
    teams.stream()
        .map(ArenaTeam::color)
        .filter(color -> !teamFailures.getOrDefault(color, false))
        .forEach(ranking::add);
    for (int index = teamFailureOrder.size() - 1; index >= 0; index--) {
      ArenaTeamColor color = teamFailureOrder.get(index);
      if (!ranking.contains(color)) {
        ranking.add(color);
      }
    }
    return List.copyOf(ranking);
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

  String tabScoreboardName() {
    return tabScoreboardName;
  }

  void setTabScoreboardName(String tabScoreboardName) {
    this.tabScoreboardName = tabScoreboardName;
  }

  String tabBossBarName() {
    return tabBossBarName;
  }

  void setTabBossBarName(String tabBossBarName) {
    this.tabBossBarName = tabBossBarName;
  }

  int nextTabLayoutRevision() {
    return ++tabLayoutRevision;
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
    if (failed && !teamFailureOrder.contains(color)) {
      teamFailureOrder.add(color);
    }
  }

  private static final class PlayerRuntimeStats {

    private final String playerName;
    private final ArenaTeamColor teamColor;
    private int kills;
    private int deaths;
    private boolean failed;
    private long failedAtMillis;

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

    private long failedAtMillis() {
      return failedAtMillis;
    }

    private void addKill() {
      kills++;
    }

    private void addDeath() {
      deaths++;
    }

    private void setFailed(boolean failed) {
      if (failed && !this.failed) {
        this.failedAtMillis = System.currentTimeMillis();
      }
      this.failed = failed;
    }

    private ArenaPlayerStats snapshot() {
      return new ArenaPlayerStats(playerName, teamColor, kills, deaths, failed);
    }
  }
}
