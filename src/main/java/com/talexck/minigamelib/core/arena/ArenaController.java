package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaActionBarConfig;
import com.talexck.minigamelib.api.arena.ArenaBossBarConfig;
import com.talexck.minigamelib.api.arena.ArenaCreateRequest;
import com.talexck.minigamelib.api.arena.ArenaHandle;
import com.talexck.minigamelib.api.arena.ArenaLayout;
import com.talexck.minigamelib.api.arena.ArenaLifecycleListener;
import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.arena.ArenaScoreboardConfig;
import com.talexck.minigamelib.api.arena.ArenaSettings;
import com.talexck.minigamelib.api.arena.ArenaStatus;
import com.talexck.minigamelib.api.arena.ArenaStopReason;
import com.talexck.minigamelib.api.arena.ArenaTemplate;
import com.talexck.minigamelib.api.arena.ArenaTitleConfig;
import com.talexck.minigamelib.api.arena.ArenaTitleFrame;
import com.talexck.minigamelib.core.world.DefaultWorldService;
import com.talexck.minigamelib.core.world.WorldCreateRequest;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ArenaController {

  private final JavaPlugin plugin;
  private final DefaultWorldService worldService;
  private final ConcurrentMap<String, ArenaTemplate> templates = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, RuntimeArena> arenas = new ConcurrentHashMap<>();

  public ArenaController(JavaPlugin plugin, DefaultWorldService worldService) {
    this.plugin = plugin;
    this.worldService = worldService;
  }

  public void registerTemplate(ArenaTemplate template) {
    Objects.requireNonNull(template, "template");
    ArenaTemplate previous = templates.putIfAbsent(template.templateId(), template);
    if (previous != null) {
      throw new IllegalStateException(
          "Arena template already registered: " + template.templateId());
    }
  }

  public boolean unregisterTemplate(String templateId) {
    return templates.remove(templateId) != null;
  }

  public Optional<ArenaTemplate> findTemplate(String templateId) {
    return Optional.ofNullable(templates.get(templateId));
  }

  public CompletableFuture<ArenaHandle> createArena(ArenaCreateRequest request) {
    Objects.requireNonNull(request, "request");
    if (arenas.containsKey(request.arenaId())) {
      return failedFuture(new IllegalStateException("Arena already exists: " + request.arenaId()));
    }

    ArenaTemplate template = templates.get(request.templateId());
    if (template == null) {
      return failedFuture(
          new IllegalArgumentException("Unknown arena template: " + request.templateId()));
    }

    ArenaLayout layout = request.layout() == null ? template.defaultLayout() : request.layout();
    ArenaSettings settings =
        request.settings() == null ? template.defaultSettings() : request.settings();
    ArenaLifecycleListener listener =
        CompositeArenaLifecycleListener.of(template.defaultListener(), request.listener());

    WorldCreateRequest worldRequest =
        new WorldCreateRequest(template.templateWorldName(), request.runtimeWorldName());

    return worldService.createRuntimeWorld(worldRequest).thenApply(runtimeWorld -> {
      RuntimeArena arena = new RuntimeArena(request.arenaId(), template.templateId(), runtimeWorld,
          layout, settings, request.initialPlayerNames(), listener);
      RuntimeArena previous = arenas.putIfAbsent(request.arenaId(), arena);
      if (previous != null) {
        throw new IllegalStateException("Arena already exists: " + request.arenaId());
      }
      setupWorld(arena);
      generateLootChests(arena);
      listener.onArenaCreated(arena.handle());
      broadcastMessagesNow(arena, arena.settings().messages().created(), 0, null);
      return arena.handle();
    });
  }

  public CompletableFuture<Void> startArena(String arenaId) {
    RuntimeArena arena = requireArena(arenaId);
    CompletableFuture<Void> future = new CompletableFuture<>();

    Bukkit.getScheduler().runTask(plugin, () -> {
      try {
        ensureStatus(arena, ArenaStatus.CREATED);
        arena.setStatus(ArenaStatus.COUNTDOWN);
        applyScoreboards(arena, arena.settings().countdownSeconds());
        applyBossBar(arena, arena.settings().countdownSeconds());
        arena.listener().onBeforeTeleport(arena.handle());
        broadcastMessagesNow(arena, arena.settings().messages().teleport(),
            arena.settings().countdownSeconds(), null);
        sendConfiguredActionBar(arena, arena.settings().actionBar().teleport(),
            arena.settings().countdownSeconds(), null);
        sendConfiguredTitle(arena, arena.settings().title().teleport(),
            arena.settings().countdownSeconds(), null);
        teleportPlayersToSpawn(arena);
        startCountdown(arena, future);
      } catch (RuntimeException exception) {
        future.completeExceptionally(exception);
      }
    });

    return future;
  }

  public CompletableFuture<Void> stopArena(String arenaId, ArenaStopReason reason) {
    RuntimeArena arena = requireArena(arenaId);
    CompletableFuture<Void> future = new CompletableFuture<>();

    Bukkit.getScheduler().runTask(plugin, () -> {
      try {
        arena.setStatus(ArenaStatus.STOPPING);
        arena.listener().onGameStopped(arena.handle(), reason);
        broadcastMessagesNow(arena, arena.settings().messages().gameStopped(), 0, reason);
        sendConfiguredActionBar(arena, arena.settings().actionBar().gameStopped(), 0, reason);
        sendConfiguredTitle(arena, arena.settings().title().gameStopped(), 0, reason);
        teleportPlayersBack(arena);
        clearBossBar(arena);
        clearScoreboards(arena);
        future.complete(null);
      } catch (RuntimeException exception) {
        future.completeExceptionally(exception);
      }
    });

    return future;
  }

  public CompletableFuture<Void> destroyArena(String arenaId) {
    RuntimeArena arena = requireArena(arenaId);
    return stopArena(arenaId, ArenaStopReason.FORCE).thenRun(() -> {
      arenas.remove(arenaId);
      worldService.unloadWorld(arena.world().world(), arena.settings().saveWorldOnUnload());
      arena.setStatus(ArenaStatus.DESTROYED);
      clearBossBar(arena);
      arena.listener().onArenaDestroyed(arena.handle());
      broadcastMessagesNow(arena, arena.settings().messages().destroyed(), 0, null);
    });
  }

  public CompletableFuture<Void> broadcastMessage(String arenaId, String message) {
    return broadcastMessages(arenaId, List.of(message));
  }

  public CompletableFuture<Void> broadcastMessages(String arenaId, List<String> messages) {
    RuntimeArena arena = requireArena(arenaId);
    CompletableFuture<Void> future = new CompletableFuture<>();

    Bukkit.getScheduler().runTask(plugin, () -> {
      try {
        broadcastMessagesNow(arena, messages, 0, null);
        future.complete(null);
      } catch (RuntimeException exception) {
        future.completeExceptionally(exception);
      }
    });

    return future;
  }

  public CompletableFuture<Void> sendActionBar(String arenaId, String message) {
    RuntimeArena arena = requireArena(arenaId);
    CompletableFuture<Void> future = new CompletableFuture<>();

    Bukkit.getScheduler().runTask(plugin, () -> {
      try {
        sendActionBarNow(arena, message, 0, null);
        future.complete(null);
      } catch (RuntimeException exception) {
        future.completeExceptionally(exception);
      }
    });

    return future;
  }

  public CompletableFuture<Void> sendTitle(String arenaId, ArenaTitleFrame title) {
    RuntimeArena arena = requireArena(arenaId);
    CompletableFuture<Void> future = new CompletableFuture<>();

    Bukkit.getScheduler().runTask(plugin, () -> {
      try {
        sendTitleNow(arena, title, 0, null);
        future.complete(null);
      } catch (RuntimeException exception) {
        future.completeExceptionally(exception);
      }
    });

    return future;
  }

  public CompletableFuture<Void> updateBossBar(String arenaId, String title, double progress) {
    RuntimeArena arena = requireArena(arenaId);
    CompletableFuture<Void> future = new CompletableFuture<>();

    Bukkit.getScheduler().runTask(plugin, () -> {
      try {
        updateBossBarNow(arena, title, progress);
        future.complete(null);
      } catch (RuntimeException exception) {
        future.completeExceptionally(exception);
      }
    });

    return future;
  }

  public Optional<ArenaHandle> findArena(String arenaId) {
    RuntimeArena arena = arenas.get(arenaId);
    return arena == null ? Optional.empty() : Optional.of(arena.handle());
  }

  public List<ArenaHandle> arenas() {
    return arenas.values().stream().map(RuntimeArena::handle).toList();
  }

  public void shutdown() {
    List<String> arenaIds = new ArrayList<>(arenas.keySet());
    for (String arenaId : arenaIds) {
      RuntimeArena arena = arenas.remove(arenaId);
      if (arena != null) {
        clearBossBar(arena);
        worldService.unloadWorld(arena.world().world(), arena.settings().saveWorldOnUnload());
        arena.setStatus(ArenaStatus.DESTROYED);
      }
    }
  }

  private void setupWorld(RuntimeArena arena) {
    World world = arena.world().world();
    ArenaPoint center = arena.layout().center();
    world.getWorldBorder().setCenter(center.x(), center.z());
    world.getWorldBorder().setSize(arena.layout().initialBorderRadius() * 2);
  }

  private void generateLootChests(RuntimeArena arena) {
    World world = arena.world().world();
    for (ArenaPoint point : arena.layout().lootChestPoints()) {
      Location location = point.toLocation(world);
      Block block = location.getBlock();
      block.setType(Material.CHEST);
      arena.listener().onLootChestGenerated(arena.handle(), point, location);
    }
  }

  private void teleportPlayersToSpawn(RuntimeArena arena) {
    World world = arena.world().world();
    List<ArenaPoint> spawnPoints = arena.layout().spawnPoints();
    List<String> players = arena.playerNames();

    for (int index = 0; index < players.size(); index++) {
      Player player = Bukkit.getPlayerExact(players.get(index));
      if (player == null) {
        continue;
      }
      ArenaPoint point = spawnPoints.get(index % spawnPoints.size());
      player.teleport(point.toLocation(world));
    }
  }

  private void teleportPlayersBack(RuntimeArena arena) {
    World returnWorld = Bukkit.getWorld(arena.settings().returnWorldName());
    if (returnWorld == null) {
      throw new IllegalStateException(
          "Return world not found: " + arena.settings().returnWorldName());
    }
    Location returnLocation = arena.settings().returnPoint().toLocation(returnWorld);
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        player.teleport(returnLocation);
      }
    }
  }

  private void startCountdown(RuntimeArena arena, CompletableFuture<Void> future) {
    int countdownSeconds = arena.settings().countdownSeconds();
    if (countdownSeconds == 0) {
      startGame(arena, future);
      return;
    }

    new BukkitRunnable() {
      private int secondsLeft = countdownSeconds;

      @Override
      public void run() {
        if (arena.status() != ArenaStatus.COUNTDOWN) {
          cancel();
          future.completeExceptionally(new IllegalStateException("Arena countdown interrupted"));
          return;
        }
        applyScoreboards(arena, secondsLeft);
        applyBossBar(arena, secondsLeft);
        arena.listener().onCountdownTick(arena.handle(), secondsLeft);
        String countdownMessage = arena.settings().messages().countdownTick();
        if (!countdownMessage.isBlank()) {
          broadcastMessageNow(arena, countdownMessage, secondsLeft, null);
        }
        sendConfiguredActionBar(arena, arena.settings().actionBar().countdownTick(),
            secondsLeft, null);
        sendConfiguredTitle(arena, arena.settings().title().countdownTick(), secondsLeft, null);
        if (secondsLeft <= 0) {
          cancel();
          startGame(arena, future);
          return;
        }
        secondsLeft--;
      }
    }.runTaskTimer(plugin, 0L, 20L);
  }

  private void startGame(RuntimeArena arena, CompletableFuture<Void> future) {
    arena.setStatus(ArenaStatus.RUNNING);
    applyScoreboards(arena, 0);
    applyBossBar(arena, 0);
    arena.listener().onGameStarted(arena.handle());
    broadcastMessagesNow(arena, arena.settings().messages().gameStarted(), 0, null);
    sendConfiguredActionBar(arena, arena.settings().actionBar().gameStarted(), 0, null);
    sendConfiguredTitle(arena, arena.settings().title().gameStarted(), 0, null);
    future.complete(null);
  }

  private void applyScoreboards(RuntimeArena arena, int secondsLeft) {
    ArenaScoreboardConfig config = arena.settings().scoreboard();
    if (!config.enabled()) {
      return;
    }

    ScoreboardManager manager = Bukkit.getScoreboardManager();
    if (manager == null) {
      return;
    }

    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null) {
        continue;
      }
      Scoreboard scoreboard = manager.getNewScoreboard();
      Component displayName = Component.text(renderText(arena, config.title(), secondsLeft, null));
      Objective objective = scoreboard.registerNewObjective("arena", Criteria.DUMMY, displayName);
      objective.setDisplaySlot(DisplaySlot.SIDEBAR);

      List<String> lines = config.lines();
      for (int index = 0; index < lines.size(); index++) {
        String line = uniqueScoreboardLine(renderText(arena, lines.get(index), secondsLeft, null),
            index);
        objective.getScore(line).setScore(lines.size() - index);
      }
      player.setScoreboard(scoreboard);
    }
  }

  private void clearScoreboards(RuntimeArena arena) {
    ScoreboardManager manager = Bukkit.getScoreboardManager();
    if (manager == null) {
      return;
    }
    Scoreboard mainScoreboard = manager.getMainScoreboard();
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        player.setScoreboard(mainScoreboard);
      }
    }
  }

  private void applyBossBar(RuntimeArena arena, int secondsLeft) {
    ArenaBossBarConfig config = arena.settings().bossBar();
    if (!config.enabled()) {
      return;
    }

    BossBar bossBar = arena.bossBar();
    if (bossBar == null) {
      bossBar = Bukkit.createBossBar(renderText(arena, config.title(), secondsLeft, null),
          config.color(), config.style());
      arena.setBossBar(bossBar);
    }

    bossBar.setTitle(renderText(arena, config.title(), secondsLeft, null));
    bossBar.setColor(config.color());
    bossBar.setStyle(config.style());
    bossBar.setProgress(resolveBossBarProgress(arena, secondsLeft));
    bossBar.removeAll();

    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        bossBar.addPlayer(player);
      }
    }
  }

  private void updateBossBarNow(RuntimeArena arena, String title, double progress) {
    BossBar bossBar = arena.bossBar();
    if (bossBar == null) {
      ArenaBossBarConfig config = arena.settings().bossBar();
      bossBar = Bukkit.createBossBar(renderText(arena, title, 0, null), config.color(),
          config.style());
      arena.setBossBar(bossBar);
    }

    bossBar.setTitle(renderText(arena, title, 0, null));
    bossBar.setProgress(clampProgress(progress));
    bossBar.removeAll();
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        bossBar.addPlayer(player);
      }
    }
  }

  private void clearBossBar(RuntimeArena arena) {
    BossBar bossBar = arena.bossBar();
    if (bossBar != null) {
      bossBar.removeAll();
      arena.setBossBar(null);
    }
  }

  private void broadcastMessagesNow(RuntimeArena arena, List<String> messages, int secondsLeft,
      ArenaStopReason reason) {
    for (String message : messages) {
      broadcastMessageNow(arena, message, secondsLeft, reason);
    }
  }

  private void broadcastMessageNow(RuntimeArena arena, String message, int secondsLeft,
      ArenaStopReason reason) {
    if (message == null || message.isBlank()) {
      return;
    }
    String rendered = renderText(arena, message, secondsLeft, reason);
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        player.sendMessage(rendered);
      }
    }
  }

  private void sendConfiguredActionBar(RuntimeArena arena, String message, int secondsLeft,
      ArenaStopReason reason) {
    ArenaActionBarConfig config = arena.settings().actionBar();
    if (!config.enabled()) {
      return;
    }
    sendActionBarNow(arena, message, secondsLeft, reason);
  }

  private void sendActionBarNow(RuntimeArena arena, String message, int secondsLeft,
      ArenaStopReason reason) {
    if (message == null || message.isBlank()) {
      return;
    }
    Component rendered = Component.text(renderText(arena, message, secondsLeft, reason));
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        player.sendActionBar(rendered);
      }
    }
  }

  private void sendConfiguredTitle(RuntimeArena arena, ArenaTitleFrame frame, int secondsLeft,
      ArenaStopReason reason) {
    ArenaTitleConfig config = arena.settings().title();
    if (!config.enabled()) {
      return;
    }
    sendTitleNow(arena, frame, secondsLeft, reason);
  }

  private void sendTitleNow(RuntimeArena arena, ArenaTitleFrame frame, int secondsLeft,
      ArenaStopReason reason) {
    if (frame == null || (frame.title().isBlank() && frame.subtitle().isBlank())) {
      return;
    }
    Title rendered = Title.title(
        Component.text(renderText(arena, frame.title(), secondsLeft, reason)),
        Component.text(renderText(arena, frame.subtitle(), secondsLeft, reason)),
        Title.Times.times(frame.fadeIn(), frame.stay(), frame.fadeOut()));
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        player.showTitle(rendered);
      }
    }
  }

  private String renderText(RuntimeArena arena, String text, int secondsLeft,
      ArenaStopReason reason) {
    return text
        .replace("{arena}", arena.arenaId())
        .replace("{template}", arena.templateId())
        .replace("{world}", arena.world().runtimeWorldName())
        .replace("{status}", arena.status().name())
        .replace("{players}", Integer.toString(arena.playerNames().size()))
        .replace("{countdown}", Integer.toString(Math.max(0, secondsLeft)))
        .replace("{reason}", reason == null ? "" : reason.name());
  }

  private String uniqueScoreboardLine(String line, int index) {
    if (line.isBlank()) {
      return scoreboardSuffix(index);
    }
    return line + scoreboardSuffix(index);
  }

  private String scoreboardSuffix(int index) {
    return "§r".repeat(index + 1);
  }

  private double resolveBossBarProgress(RuntimeArena arena, int secondsLeft) {
    ArenaBossBarConfig config = arena.settings().bossBar();
    int countdownSeconds = arena.settings().countdownSeconds();
    if (config.countdownProgress() && arena.status() == ArenaStatus.COUNTDOWN
        && countdownSeconds > 0) {
      return clampProgress((double) secondsLeft / countdownSeconds);
    }
    return clampProgress(config.runningProgress());
  }

  private double clampProgress(double progress) {
    return Math.max(0.0, Math.min(1.0, progress));
  }

  private RuntimeArena requireArena(String arenaId) {
    RuntimeArena arena = arenas.get(arenaId);
    if (arena == null) {
      throw new IllegalArgumentException("Unknown arena: " + arenaId);
    }
    return arena;
  }

  private void ensureStatus(RuntimeArena arena, ArenaStatus expected) {
    if (arena.status() != expected) {
      throw new IllegalStateException(
          "Arena " + arena.arenaId() + " status is " + arena.status() + ", expected " + expected);
    }
  }

  private static <T> CompletableFuture<T> failedFuture(Throwable throwable) {
    CompletableFuture<T> future = new CompletableFuture<>();
    future.completeExceptionally(throwable);
    return future;
  }
}
