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
  private boolean placementsAwarded;
  private long lastLayoutRefreshMillis;

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
    return team.playerNames().stream()
        .map(playerStats::get)
        .filter(stats -> stats != null)
        .mapToInt(PlayerRuntimeStats::score)
        .sum();
  }

  int teamScore(ArenaTeamColor color) {
    return teams.stream().filter(team -> team.color() == color).findFirst()
        .map(team -> teamScore(team, 0L)).orElse(0);
  }

  void addScore(String playerName, int amount) {
    PlayerRuntimeStats stats = playerStats.get(playerName);
    if (stats != null && amount > 0) {
      stats.addScore(amount);
    }
  }

  int score(String playerName) {
    PlayerRuntimeStats stats = playerStats.get(playerName);
    return stats == null ? 0 : stats.score();
  }

  int kills(String playerName) {
    PlayerRuntimeStats stats = playerStats.get(playerName);
    return stats == null ? 0 : stats.kills();
  }

  int deaths(String playerName) {
    PlayerRuntimeStats stats = playerStats.get(playerName);
    return stats == null ? 0 : stats.deaths();
  }

  long alivePlayerCount() {
    return playerNames.stream().filter(name -> !isFailed(name)).count();
  }

  long alivePlayers(ArenaTeamColor color) {
    return teams.stream().filter(team -> team.color() == color).findFirst()
        .map(team -> team.playerNames().stream().filter(name -> !isFailed(name)).count())
        .orElse(0L);
  }

  /** Seconds left in the round, or -1 when there is no time limit or the game has not started. */
  long secondsLeft(long nowMillis) {
    if (!settings.rules().hasTimeLimit() || gameStartedAtMillis <= 0L) {
      return -1L;
    }
    long elapsed = nowMillis - gameStartedAtMillis;
    return Math.max(0L, (settings.rules().timeLimit().toMillis() - elapsed + 999L) / 1000L);
  }

  long elapsedSeconds(long nowMillis) {
    return gameStartedAtMillis <= 0L ? 0L : Math.max(0L, (nowMillis - gameStartedAtMillis) / 1000L);
  }

  /** Awards the placement bonus of {@link #finalTeamRanking()} once. */
  void awardPlacementScores() {
    if (placementsAwarded) {
      return;
    }
    placementsAwarded = true;
    List<ArenaTeamColor> ranking = finalTeamRanking();
    for (int index = 0; index < ranking.size(); index++) {
      int bonus = settings.rules().placementScore(index);
      ArenaTeamColor color = ranking.get(index);
      teams.stream().filter(team -> team.color() == color).findFirst()
          .ifPresent(team -> team.playerNames().forEach(name -> addScore(name, bonus)));
    }
  }

  int placementOf(ArenaTeamColor color) {
    int index = finalTeamRanking().indexOf(color);
    return index < 0 ? 0 : index + 1;
  }

  int teamKills(ArenaTeam team) {
    return team.playerNames().stream()
        .map(playerStats::get)
        .filter(stats -> stats != null)
        .mapToInt(PlayerRuntimeStats::kills)
        .sum();
  }

  /**
   * Teams still alive first (most alive players, then highest score), then eliminated teams in
   * reverse elimination order.
   */
  List<ArenaTeamColor> finalTeamRanking() {
    List<ArenaTeamColor> ranking = new ArrayList<>();
    teams.stream()
        .map(ArenaTeam::color)
        .filter(color -> !teamFailures.getOrDefault(color, false))
        .sorted(java.util.Comparator.<ArenaTeamColor>comparingLong(this::alivePlayers).reversed()
            .thenComparing(java.util.Comparator.<ArenaTeamColor>comparingInt(this::teamScore)
                .reversed()))
        .forEach(ranking::add);
    for (int index = teamFailureOrder.size() - 1; index >= 0; index--) {
      ArenaTeamColor color = teamFailureOrder.get(index);
      if (!ranking.contains(color)) {
        ranking.add(color);
      }
    }
    return List.copyOf(ranking);
  }

  /** Marks a player as failed without counting a death (used for disconnects before start). */
  void markFailedWithoutDeath(String playerName) {
    PlayerRuntimeStats stats = playerStats.get(playerName);
    if (stats != null) {
      stats.setFailed(true);
      updateTeamFailure(stats.teamColor());
    }
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
          isTeamFailed(team.color()), teamScore(team, 0L), placementOf(team.color()));
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

  long lastLayoutRefreshMillis() {
    return lastLayoutRefreshMillis;
  }

  void setLastLayoutRefreshMillis(long lastLayoutRefreshMillis) {
    this.lastLayoutRefreshMillis = lastLayoutRefreshMillis;
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
    private int score;
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

    private int score() {
      return score;
    }

    private void addScore(int amount) {
      score += amount;
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
      return new ArenaPlayerStats(playerName, teamColor, kills, deaths, failed, score);
    }
  }
}
