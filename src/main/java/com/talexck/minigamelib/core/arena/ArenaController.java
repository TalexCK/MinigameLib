package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaCreateRequest;
import com.talexck.minigamelib.api.arena.ArenaGameResult;
import com.talexck.minigamelib.api.arena.ArenaHandle;
import com.talexck.minigamelib.api.arena.ArenaLayout;
import com.talexck.minigamelib.api.arena.ArenaLifecycleListener;
import com.talexck.minigamelib.api.arena.ArenaSettings;
import com.talexck.minigamelib.api.arena.ArenaSound;
import com.talexck.minigamelib.api.arena.ArenaStatus;
import com.talexck.minigamelib.api.arena.ArenaStopReason;
import com.talexck.minigamelib.api.arena.ArenaTeam;
import com.talexck.minigamelib.api.arena.ArenaTeamColor;
import com.talexck.minigamelib.api.arena.ArenaTeamSpawn;
import com.talexck.minigamelib.api.arena.ArenaTemplate;
import com.talexck.minigamelib.api.stats.StatsService;
import com.talexck.minigamelib.api.arena.ArenaTitleFrame;
import com.talexck.minigamelib.core.chest.DefaultChestService;
import com.talexck.minigamelib.core.resourcepack.ResourcePackService;
import com.talexck.minigamelib.core.world.DefaultWorldService;
import com.talexck.minigamelib.core.world.WorldCreateRequest;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ArenaController implements ArenaLifecycleControl {

  private static final long POST_GAME_RETURN_DELAY_TICKS = 20L * 15L;

  private final JavaPlugin plugin;
  private final DefaultWorldService worldService;
  private final DefaultChestService chestService;
  private final ResourcePackService resourcePackService;
  private final ConcurrentMap<String, ArenaTemplate> templates = new ConcurrentHashMap<>();
  private final ArenaRegistry registry = new ArenaRegistry();
  private final SpawnCageService spawnCageService = new SpawnCageService();
  private final LootService lootService;
  private final BoundaryService boundaryService;
  private final ItemService itemService;
  private final TabDisplayService tabDisplayService;
  private final DisplayService displayService;
  private final CombatService combatService;
  private final ItemCombatService itemCombatService;
  private final PlayerEnvironmentService playerEnvironmentService;
  private final StatsService statsService;

  public ArenaController(JavaPlugin plugin, DefaultWorldService worldService,
      StatsService statsService) {
    this.plugin = plugin;
    this.worldService = worldService;
    this.statsService = statsService;
    this.chestService = new DefaultChestService(plugin);
    this.resourcePackService = new ResourcePackService(plugin);
    this.lootService = new LootService(chestService);
    this.itemService = new ItemService(plugin, registry);
    this.tabDisplayService = new TabDisplayService(plugin, registry);
    this.displayService = new DisplayService(tabDisplayService);
    this.combatService = new CombatService(plugin, registry, displayService, this);
    this.boundaryService = new BoundaryService(plugin, registry, chestService, combatService);
    this.itemCombatService = new ItemCombatService(plugin, registry, itemService, combatService);
    this.playerEnvironmentService = new PlayerEnvironmentService(plugin, spawnCageService,
        displayService, tabDisplayService, resourcePackService, this::allKnownSettings);
  }

  private java.util.List<ArenaSettings> allKnownSettings() {
    java.util.List<ArenaSettings> settings = new java.util.ArrayList<>();
    registry.stream().map(RuntimeArena::settings).forEach(settings::add);
    templates.values().stream().map(ArenaTemplate::defaultSettings).forEach(settings::add);
    return settings;
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
    if (registry.contains(request.arenaId())) {
      return failedFuture(new IllegalStateException("Arena already exists: " + request.arenaId()));
    }

    ArenaTemplate template = templates.get(request.templateId());
    if (template == null) {
      return failedFuture(
          new IllegalArgumentException("Unknown arena template: " + request.templateId()));
    }
    if (request.initialPlayerNames().size() < 2 && !request.allowSinglePlayer()) {
      return failedFuture(
          new IllegalArgumentException("At least 2 players are required to create an arena"));
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
          layout, settings, resolveTeams(request), listener);
      RuntimeArena previous = registry.putIfAbsent(arena);
      if (previous != null) {
        throw new IllegalStateException("Arena already exists: " + request.arenaId());
      }
      setupWorld(arena);
      lootService.start(arena);
      listener.onArenaCreated(arena.handle());
      displayService.broadcastMessages(arena, arena.settings().messages().created(), 0, null);
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
        displayService.applyScoreboards(arena, arena.settings().countdownSeconds());
        displayService.applyBossBar(arena, arena.settings().countdownSeconds());
        arena.listener().onBeforeTeleport(arena.handle());
        displayService.broadcastMessages(arena, arena.settings().messages().teleport(),
            arena.settings().countdownSeconds(), null);
        displayService.sendConfiguredActionBar(arena, arena.settings().actionBar().teleport(),
            arena.settings().countdownSeconds(), null);
        displayService.sendConfiguredTitle(arena, arena.settings().title().teleport(),
            arena.settings().countdownSeconds(), null);
        displayService.playConfiguredSound(arena, arena.settings().sounds().teleport());
        playerEnvironmentService.teleportPlayersToSpawn(arena);
        itemService.giveBeginningItems(arena);
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
        if (arena.status() == ArenaStatus.STOPPING || arena.status() == ArenaStatus.STOPPED
            || arena.status() == ArenaStatus.DESTROYED) {
          future.complete(null);
          return;
        }
        arena.setStatus(ArenaStatus.STOPPING);
        arena.listener().onGameStopped(arena.handle(), reason);
        displayService.broadcastMessages(arena, arena.settings().messages().gameStopped(), 0,
            reason);
        displayService.sendConfiguredActionBar(arena, arena.settings().actionBar().gameStopped(), 0,
            reason);
        displayService.sendConfiguredTitle(arena, arena.settings().title().gameStopped(), 0,
            reason);
        displayService.playConfiguredSound(arena, arena.settings().sounds().gameStopped());
        playerEnvironmentService.setArenaPlayersGameMode(arena, GameMode.SPECTATOR);
        itemService.clearArenaPlayerInventories(arena);
        spawnCageService.clear(arena.arenaId());
        chestService.stopArenaChests(arenaId, false);
        boundaryService.cancelTasks(arena);
        boundaryService.remove(arenaId);
        displayService.clearBossBar(arena);
        displayService.clearScoreboards(arena);
        ArenaGameResult result = gameResult(arena, reason);
        arena.listener().onGameEnded(arena.handle(), result);
        if (statsService != null) {
          statsService.recordGameResult(result);
        }
        displayService.broadcastFinalTeamRanking(arena, tabDisplayService.gameName());
        Bukkit.getScheduler().runTaskLater(plugin, () -> finishStoppedArena(arena, future),
            POST_GAME_RETURN_DELAY_TICKS);
      } catch (RuntimeException exception) {
        future.completeExceptionally(exception);
      }
    });

    return future;
  }

  public CompletableFuture<Void> destroyArena(String arenaId) {
    return stopArena(arenaId, ArenaStopReason.FORCE);
  }

  @Override
  public CompletableFuture<Void> stop(String arenaId, ArenaStopReason reason) {
    return stopArena(arenaId, reason);
  }

  public CompletableFuture<Void> broadcastMessage(String arenaId, String message) {
    return broadcastMessages(arenaId, List.of(message));
  }

  public CompletableFuture<Void> broadcastMessages(String arenaId, List<String> messages) {
    RuntimeArena arena = requireArena(arenaId);
    CompletableFuture<Void> future = new CompletableFuture<>();

    Bukkit.getScheduler().runTask(plugin, () -> {
      try {
        displayService.broadcastMessages(arena, messages, 0, null);
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
        displayService.sendActionBar(arena, message, 0, null);
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
        displayService.sendTitle(arena, title, 0, null);
        future.complete(null);
      } catch (RuntimeException exception) {
        future.completeExceptionally(exception);
      }
    });

    return future;
  }

  public CompletableFuture<Void> playSound(String arenaId, ArenaSound sound) {
    RuntimeArena arena = requireArena(arenaId);
    CompletableFuture<Void> future = new CompletableFuture<>();

    Bukkit.getScheduler().runTask(plugin, () -> {
      try {
        displayService.playSound(arena, sound);
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
        displayService.updateBossBar(arena, title, progress);
        future.complete(null);
      } catch (RuntimeException exception) {
        future.completeExceptionally(exception);
      }
    });

    return future;
  }

  public Optional<ArenaHandle> findArena(String arenaId) {
    RuntimeArena arena = registry.get(arenaId);
    return arena == null ? Optional.empty() : Optional.of(arena.handle());
  }

  public List<ArenaHandle> arenas() {
    return registry.stream().map(RuntimeArena::handle).toList();
  }

  public void shutdown() {
    List<String> arenaIds = registry.ids();
    for (String arenaId : arenaIds) {
      RuntimeArena arena = registry.remove(arenaId);
      if (arena != null) {
        chestService.stopArenaChests(arenaId, false);
        boundaryService.cancelTasks(arena);
        boundaryService.remove(arenaId);
        spawnCageService.clear(arena.arenaId());
        displayService.clearBossBar(arena);
        worldService.unloadWorld(arena.world().world(), arena.settings().saveWorldOnUnload());
        arena.setStatus(ArenaStatus.DESTROYED);
      }
    }
    boundaryService.shutdown();
    itemService.shutdown();
    itemCombatService.shutdown();
    combatService.shutdown();
    playerEnvironmentService.shutdown();
    tabDisplayService.shutdown();
    resourcePackService.shutdown();
  }

  private void setupWorld(RuntimeArena arena) {
    arena.world().world().setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
    boundaryService.init(arena);
  }

  private void finishStoppedArena(RuntimeArena arena, CompletableFuture<Void> future) {
    try {
      if (arena.status() == ArenaStatus.DESTROYED) {
        future.complete(null);
        return;
      }
      playerEnvironmentService.teleportPlayersBack(arena);
      chestService.stopArenaChests(arena.arenaId(), true);
      boundaryService.cancelTasks(arena);
      boundaryService.remove(arena.arenaId());
      spawnCageService.clear(arena.arenaId());
      displayService.clearBossBar(arena);
      displayService.clearScoreboards(arena);
      arena.setStatus(ArenaStatus.STOPPED);
      registry.remove(arena.arenaId(), arena);
      boolean unloaded = worldService.unloadWorld(arena.world().world(), false);
      arena.setStatus(ArenaStatus.DESTROYED);
      arena.listener().onArenaDestroyed(arena.handle());
      displayService.broadcastMessages(arena, arena.settings().messages().destroyed(), 0, null);
      if (!unloaded) {
        plugin.getLogger().warning(
            "Runtime world unload failed, skip delete: " + arena.world().runtimeWorldName());
        future.complete(null);
        return;
      }
      worldService.deleteWorldDirectory(arena.world().runtimeWorldName())
          .whenComplete((deleted, exception) -> {
            if (exception != null) {
              plugin.getLogger().warning("Runtime world delete failed: "
                  + arena.world().runtimeWorldName() + " - " + exception.getMessage());
              future.completeExceptionally(exception);
              return;
            }
            future.complete(null);
          });
    } catch (RuntimeException exception) {
      future.completeExceptionally(exception);
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
        displayService.applyScoreboards(arena, secondsLeft);
        displayService.applyBossBar(arena, secondsLeft);
        arena.listener().onCountdownTick(arena.handle(), secondsLeft);
        String countdownMessage = arena.settings().messages().countdownTick();
        if (!countdownMessage.isBlank()) {
          displayService.broadcastMessage(arena, countdownMessage, secondsLeft, null);
        }
        displayService.sendCountdownTitle(arena, secondsLeft);
        displayService.playCountdownSound(arena, secondsLeft);
        displayService.sendConfiguredActionBar(arena, arena.settings().actionBar().countdownTick(),
            secondsLeft, null);
        displayService.sendConfiguredTitle(arena, arena.settings().title().countdownTick(),
            secondsLeft, null);
        displayService.playConfiguredSound(arena, arena.settings().sounds().countdownTick());
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
    arena.markGameStarted(System.currentTimeMillis());
    spawnCageService.clear(arena.arenaId());
    playerEnvironmentService.setArenaPlayersGameMode(arena, GameMode.SURVIVAL);
    itemService.startInfiniteBlockMaintenance(arena);
    boundaryService.startLifecycle(arena);
    boundaryService.scheduleStages(arena);
    displayService.applyScoreboards(arena, 0);
    displayService.applyBossBar(arena, 0);
    arena.listener().onGameStarted(arena.handle());
    displayService.broadcastMessages(arena, arena.settings().messages().gameStarted(), 0, null);
    displayService.sendConfiguredActionBar(arena, arena.settings().actionBar().gameStarted(), 0,
        null);
    displayService.sendConfiguredTitle(arena, arena.settings().title().gameStarted(), 0, null);
    displayService.playConfiguredSound(arena, arena.settings().sounds().gameStarted());
    future.complete(null);
  }

  private List<ArenaTeam> resolveTeams(ArenaCreateRequest request) {
    ArenaLayout layout = request.layout() != null ? request.layout()
        : templates.get(request.templateId()).defaultLayout();
    ArenaSettings settings = request.settings() != null ? request.settings()
        : templates.get(request.templateId()).defaultSettings();
    List<ArenaTeamColor> configuredColors =
        layout.teamSpawns().stream().map(ArenaTeamSpawn::color).distinct().toList();
    return TeamDistribution.resolveTeams(request.initialPlayerNames(), configuredColors,
        settings.maxTeamSize());
  }

  private ArenaGameResult gameResult(RuntimeArena arena, ArenaStopReason reason) {
    return new ArenaGameResult(arena.arenaId(), arena.winningTeam(), arena.teamStats(),
        arena.playerStats(), reason);
  }

  private RuntimeArena requireArena(String arenaId) {
    RuntimeArena arena = registry.get(arenaId);
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
