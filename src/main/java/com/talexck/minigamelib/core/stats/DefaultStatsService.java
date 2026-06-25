package com.talexck.minigamelib.core.stats;

import com.talexck.minigamelib.api.arena.ArenaGameResult;
import com.talexck.minigamelib.api.arena.ArenaPlayerStats;
import com.talexck.minigamelib.api.arena.ArenaStopReason;
import com.talexck.minigamelib.api.arena.ArenaTeamStats;
import com.talexck.minigamelib.api.stats.LeaderboardEntry;
import com.talexck.minigamelib.api.stats.LeaderboardType;
import com.talexck.minigamelib.api.stats.StatsBoard;
import com.talexck.minigamelib.api.stats.StatsService;
import com.talexck.minigamelib.api.stats.StatsSettings;
import com.talexck.minigamelib.api.stats.StatsStorageConfig;
import com.talexck.minigamelib.api.stats.StatsStorageType;
import com.talexck.minigamelib.api.stats.StoredPlayerStats;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DefaultStatsService implements StatsService {

  private static final int BOARD_LIMIT = 10;
  private static final String BOARD_ID_PATTERN = "[A-Za-z0-9_-]{1,48}";

  private final JavaPlugin plugin;
  private final ExecutorService executor;
  private int killExperience = 10;
  private int winExperience = 50;
  private JdbcStatsRepository repository;
  private BukkitTask refreshTask;
  private volatile boolean available;

  public DefaultStatsService(JavaPlugin plugin) {
    this.plugin = plugin;
    this.executor = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, "MinigameLib-Stats");
      thread.setDaemon(true);
      return thread;
    });
  }

  @Override
  public synchronized void configure(StatsSettings settings) {
    closeRepository();
    cancelRefreshTask();
    if (settings == null) {
      plugin.getLogger().warning("MinigameLib stats disabled: no settings were provided.");
      return;
    }
    this.killExperience = settings.killExperience();
    this.winExperience = settings.winExperience();
    connect(settings.storage());
    if (available) {
      Bukkit.getScheduler().runTaskLater(plugin, this::refreshBoards, 20L);
      this.refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshBoards,
          20L * 60L, 20L * 60L);
    }
  }

  private void connect(StatsStorageConfig storage) {
    try {
      StatsStorageConfig resolved = storage == null ? StatsStorageConfig.sqlite("stats.db") : storage;
      StatsStorageType type = resolved.type();
      String jdbcUrl = jdbcUrl(resolved);
      this.repository = new JdbcStatsRepository(type, jdbcUrl, resolved.username(),
          resolved.password());
      this.available = true;
      plugin.getLogger().info("MinigameLib stats storage connected: " + type);
    } catch (RuntimeException | SQLException | ClassNotFoundException exception) {
      this.available = false;
      plugin.getLogger().severe("MinigameLib stats storage unavailable: " + exception.getMessage());
    }
  }

  private String jdbcUrl(StatsStorageConfig storage) {
    if (storage.type() == StatsStorageType.SQLITE) {
      File file = new File(storage.sqliteFile());
      File parent = file.getParentFile();
      if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
        throw new IllegalStateException("无法创建统计数据目录：" + parent.getAbsolutePath());
      }
      return "jdbc:sqlite:" + file.getAbsolutePath();
    }
    if (storage.jdbcUrl().isBlank()) {
      throw new IllegalArgumentException(storage.type() + " storage requires jdbc-url");
    }
    return storage.jdbcUrl();
  }

  @Override
  public boolean isAvailable() {
    return available;
  }

  @Override
  public CompletableFuture<Void> recordGameResult(ArenaGameResult result) {
    if (!available || result == null) {
      return CompletableFuture.completedFuture(null);
    }
    return CompletableFuture.runAsync(() -> {
      try {
        for (PlayerStatDelta delta : deltas(result)) {
          repository.addPlayerDelta(delta);
        }
      } catch (SQLException exception) {
        throw new CompletionException(exception);
      }
    }, executor).whenComplete((ignored, exception) -> {
      if (exception != null) {
        plugin.getLogger().warning("Failed to record game stats: " + rootMessage(exception));
      } else {
        refreshBoards();
      }
    });
  }

  private List<PlayerStatDelta> deltas(ArenaGameResult result) {
    if (result.reason() != ArenaStopReason.NORMAL) {
      return List.of();
    }
    Set<String> winnerNames = winnerNames(result);
    return result.playerStats().stream().map(stats -> delta(stats, winnerNames))
        .filter(delta -> delta.kills() > 0 || delta.wins() > 0 || delta.experience() > 0)
        .toList();
  }

  private PlayerStatDelta delta(ArenaPlayerStats stats, Set<String> winnerNames) {
    int kills = Math.max(0, stats.kills());
    int wins = winnerNames.contains(stats.playerName()) ? 1 : 0;
    int experience = kills * killExperience + wins * winExperience;
    return new PlayerStatDelta(playerId(stats.playerName()), stats.playerName(), kills, wins,
        experience);
  }

  private Set<String> winnerNames(ArenaGameResult result) {
    if (result.winningTeam() == null) {
      return Set.of();
    }
    Set<String> names = new HashSet<>();
    for (ArenaTeamStats team : result.teamStats()) {
      if (team.color() == result.winningTeam()) {
        names.addAll(team.playerNames());
      }
    }
    return Set.copyOf(names);
  }

  private UUID playerId(String playerName) {
    Player player = Bukkit.getPlayerExact(playerName);
    if (player != null) {
      return player.getUniqueId();
    }
    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
    return offlinePlayer.getUniqueId();
  }

  @Override
  public CompletableFuture<Optional<StoredPlayerStats>> playerStats(UUID playerId) {
    if (!available) {
      return CompletableFuture.completedFuture(Optional.empty());
    }
    return CompletableFuture.supplyAsync(() -> {
      try {
        return repository.playerStats(playerId);
      } catch (SQLException exception) {
        throw new CompletionException(exception);
      }
    }, executor);
  }

  @Override
  public CompletableFuture<List<LeaderboardEntry>> top(LeaderboardType type, int limit) {
    if (!available) {
      return CompletableFuture.completedFuture(List.of());
    }
    return CompletableFuture.supplyAsync(() -> {
      try {
        return repository.top(type, limit);
      } catch (SQLException exception) {
        throw new CompletionException(exception);
      }
    }, executor);
  }

  @Override
  public CompletableFuture<StatsBoard> createBoard(LeaderboardType type, String id,
      Location location) {
    if (!available) {
      return failedFuture(new IllegalStateException("统计服务不可用"));
    }
    validateBoardId(id);
    StatsBoard board = StatsBoard.fromLocation(type, id, location);
    return CompletableFuture.supplyAsync(() -> {
      try {
        repository.saveBoard(board);
        return board;
      } catch (SQLException exception) {
        throw new CompletionException(exception);
      }
    }, executor).whenComplete((savedBoard, exception) -> {
      if (exception == null) {
        refreshBoard(savedBoard);
      }
    });
  }

  @Override
  public CompletableFuture<Boolean> deleteBoard(LeaderboardType type, String id) {
    if (!available) {
      return CompletableFuture.completedFuture(false);
    }
    validateBoardId(id);
    return CompletableFuture.supplyAsync(() -> {
      try {
        return repository.deleteBoard(type, id);
      } catch (SQLException exception) {
        throw new CompletionException(exception);
      }
    }, executor).whenComplete((deleted, exception) -> {
      if (exception == null && Boolean.TRUE.equals(deleted)) {
        Bukkit.getScheduler().runTask(plugin, () -> removeHologram(type, id));
      }
    });
  }

  @Override
  public CompletableFuture<List<StatsBoard>> boards(LeaderboardType type) {
    if (!available) {
      return CompletableFuture.completedFuture(List.of());
    }
    return CompletableFuture.supplyAsync(() -> {
      try {
        return repository.boards(type);
      } catch (SQLException exception) {
        throw new CompletionException(exception);
      }
    }, executor);
  }

  @Override
  public void refreshBoards() {
    if (!available) {
      return;
    }
    CompletableFuture.supplyAsync(() -> {
      try {
        return repository.allBoards();
      } catch (SQLException exception) {
        throw new CompletionException(exception);
      }
    }, executor).thenAccept(boards -> boards.forEach(this::refreshBoard))
        .exceptionally(exception -> {
          plugin.getLogger().warning("Failed to refresh stat boards: " + rootMessage(exception));
          return null;
        });
  }

  private void refreshBoard(StatsBoard board) {
    top(board.type(), BOARD_LIMIT).thenAccept(entries -> Bukkit.getScheduler().runTask(plugin,
        () -> renderHologram(board, entries))).exceptionally(exception -> {
      plugin.getLogger().warning("Failed to refresh stat board " + board.id() + ": "
          + rootMessage(exception));
      return null;
    });
  }

  private void renderHologram(StatsBoard board, List<LeaderboardEntry> entries) {
    if (!Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
      return;
    }
    Location location = board.toLocation();
    if (location == null) {
      plugin.getLogger().warning("Stat board world not loaded: " + board.worldName());
      return;
    }
    String name = hologramName(board.type(), board.id());
    List<String> lines = lines(board.type(), entries);
    Hologram hologram = DHAPI.getHologram(name);
    if (hologram == null) {
      DHAPI.createHologram(name, location, false, lines);
      return;
    }
    DHAPI.moveHologram(hologram, location);
    DHAPI.setHologramLines(hologram, lines);
  }

  private List<String> lines(LeaderboardType type, List<LeaderboardEntry> entries) {
    List<String> lines = new java.util.ArrayList<>();
    lines.add("&b&l✦ " + type.displayName() + "总榜 ✦");
    lines.add("&8Top 10");
    if (entries.isEmpty()) {
      lines.add("&7暂无数据");
      return List.copyOf(lines);
    }
    for (int index = 0; index < Math.min(BOARD_LIMIT, entries.size()); index++) {
      LeaderboardEntry entry = entries.get(index);
      lines.add(rankPrefix(index) + " &f" + entry.playerName() + " &8» &e" + entry.value());
    }
    return List.copyOf(lines);
  }

  private String rankPrefix(int index) {
    return switch (index) {
      case 0 -> "&6&l#1";
      case 1 -> "&f&l#2";
      case 2 -> "&c&l#3";
      default -> "&7#" + (index + 1);
    };
  }

  private void removeHologram(LeaderboardType type, String id) {
    if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
      DHAPI.removeHologram(hologramName(type, id));
    }
  }

  private String hologramName(LeaderboardType type, String id) {
    return "mgl-board-" + type.key() + "-" + id;
  }

  private void validateBoardId(String id) {
    if (id == null || !id.matches(BOARD_ID_PATTERN)) {
      throw new IllegalArgumentException("榜单 id 只能包含字母、数字、下划线或横线，长度 1-48");
    }
  }

  private String rootMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current.getMessage();
  }

  private static <T> CompletableFuture<T> failedFuture(Throwable throwable) {
    CompletableFuture<T> future = new CompletableFuture<>();
    future.completeExceptionally(throwable);
    return future;
  }

  private void cancelRefreshTask() {
    if (refreshTask != null) {
      refreshTask.cancel();
      refreshTask = null;
    }
  }

  private void closeRepository() {
    available = false;
    if (repository != null) {
      try {
        repository.close();
      } catch (SQLException exception) {
        plugin.getLogger().warning("Failed to close stats storage: " + exception.getMessage());
      } finally {
        repository = null;
      }
    }
  }

  public void shutdown() {
    cancelRefreshTask();
    executor.shutdownNow();
    closeRepository();
  }
}
