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
import com.talexck.minigamelib.core.lang.LanguageService;
import com.talexck.minigamelib.core.resourcepack.ResourcePackService;
import com.talexck.minigamelib.core.world.DefaultWorldService;
import com.talexck.minigamelib.core.world.WorldCreateRequest;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ArenaController implements ArenaLifecycleControl {

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
      StatsService statsService, LanguageService language) {
    this.plugin = plugin;
    this.worldService = worldService;
    this.statsService = statsService;
    TeamPalette.setNameResolver(color ->
        language.text("team." + color.name().toLowerCase(java.util.Locale.ROOT)));
    this.chestService = new DefaultChestService(plugin);
    this.resourcePackService = new ResourcePackService(plugin);
    this.lootService = new LootService(chestService);
    this.itemService = new ItemService(plugin, registry);
    this.tabDisplayService = new TabDisplayService(plugin, registry, language);
    this.displayService = new DisplayService(tabDisplayService, language);
    this.combatService = new CombatService(plugin, registry, displayService, this);
    this.boundaryService =
        new BoundaryService(plugin, registry, chestService, combatService, language);
    this.displayService.setBoundaryStatus(boundaryService::statusText);
    this.itemCombatService = new ItemCombatService(plugin, registry, itemService, combatService);
    this.playerEnvironmentService = new PlayerEnvironmentService(plugin, registry,
        spawnCageService, displayService, tabDisplayService, resourcePackService,
        this::allKnownSettings);
  }

  /** Hook run for each player sent back to the return point after a game. */
  public void setReturnHandler(java.util.function.Consumer<org.bukkit.entity.Player> handler) {
    playerEnvironmentService.setReturnHandler(handler);
  }

  /** Whether the player takes part in an arena that is counting down or running. */
  public boolean isPlaying(org.bukkit.entity.Player player) {
    return playerEnvironmentService.isPlaying(player);
  }

  /** Whether the player belongs to any arena that has not been destroyed yet. */
  public boolean isInArena(String playerName) {
    return registry.isInActiveArena(playerName);
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
        // Anyone who went offline between matchmaking and start forfeits, or the game could never
        // end.
        for (String playerName : arena.playerNames()) {
          if (Bukkit.getPlayerExact(playerName) == null) {
            arena.markFailedWithoutDeath(playerName);
          }
        }
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
        boolean wasRunning = arena.status() == ArenaStatus.RUNNING;
        if (reason == ArenaStopReason.TIME_UP && arena.winningTeam() == null) {
          arena.finalTeamRanking().stream().findFirst()
              .filter(color -> !arena.isTeamFailed(color))
              .ifPresent(arena::setWinningTeam);
        }
        arena.setStatus(ArenaStatus.STOPPING);
        if (reason.finishedNaturally()) {
          arena.awardPlacementScores();
        }
        arena.listener().onGameStopped(arena.handle(), reason);
        if (reason == ArenaStopReason.TIME_UP) {
          displayService.broadcastMessage(arena,
              arena.settings().presentation().timeUpMessage(), 0, reason);
        }
        displayService.broadcastMessages(arena, arena.settings().messages().gameStopped(), 0,
            reason);
        displayService.sendConfiguredActionBar(arena, arena.settings().actionBar().gameStopped(), 0,
            reason);
        if (wasRunning && reason.finishedNaturally()) {
          sendVictoryFeedback(arena);
        } else {
          displayService.sendConfiguredTitle(arena, arena.settings().title().gameStopped(), 0,
              reason);
        }
        displayService.playConfiguredSound(arena, arena.settings().sounds().gameStopped());
        playerEnvironmentService.setArenaPlayersGameMode(arena, GameMode.SPECTATOR);
        itemService.clearArenaPlayerInventories(arena);
        spawnCageService.clear(arena.arenaId());
        chestService.stopArenaChests(arenaId, false);
        boundaryService.cancelTasks(arena);
        boundaryService.remove(arenaId);
        displayService.clearBossBar(arena);
        // Keep the final sidebar/tablist visible during the post-game screen; they are reset
        // when players are sent back.
        displayService.applyScoreboards(arena, 0);
        ArenaGameResult result = gameResult(arena, reason);
        arena.listener().onGameEnded(arena.handle(), result);
        if (statsService != null) {
          statsService.recordGameResult(result);
        }
        if (wasRunning) {
          displayService.broadcastFinalTeamRanking(arena);
        }
        long returnDelay = wasRunning ? 20L * arena.settings().rules().postGameSeconds() : 1L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> finishStoppedArena(arena, future),
            Math.max(1L, returnDelay));
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
        displayService.clearScoreboards(arena);
        try {
          playerEnvironmentService.teleportPlayersBack(arena);
        } catch (RuntimeException exception) {
          plugin.getLogger().warning("Could not return players of " + arenaId + ": "
              + exception.getMessage());
        }
        boolean unloaded =
            worldService.unloadWorld(arena.world().world(), arena.settings().saveWorldOnUnload());
        if (unloaded) {
          worldService.deleteRuntimeWorldNow(arena.world());
        }
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
      displayService.clearBossBar(arena);
      displayService.clearScoreboards(arena);
      playerEnvironmentService.teleportPlayersBack(arena);
      playerEnvironmentService.evacuateWorld(arena);
      chestService.stopArenaChests(arena.arenaId(), true);
      boundaryService.cancelTasks(arena);
      boundaryService.remove(arena.arenaId());
      spawnCageService.clear(arena.arenaId());
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
      worldService.deleteRuntimeWorld(arena.world())
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
        displayService.tickScoreboards(arena, secondsLeft);
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
    startGameClock(arena);
    future.complete(null);
    // Somebody may have left during the countdown and handed the game to one team already.
    combatService.checkVictory(arena);
  }

  /** One-second heartbeat: HUD refresh and the round time limit. */
  private void startGameClock(RuntimeArena arena) {
    BukkitTask task = new BukkitRunnable() {
      @Override
      public void run() {
        if (arena.status() != ArenaStatus.RUNNING) {
          cancel();
          return;
        }
        if (arena.settings().rules().hasTimeLimit()
            && arena.secondsLeft(System.currentTimeMillis()) <= 0L) {
          cancel();
          stopArena(arena.arenaId(), ArenaStopReason.TIME_UP);
          return;
        }
        long secondsLeft = arena.secondsLeft(System.currentTimeMillis());
        if (secondsLeft > 0 && secondsLeft <= 10) {
          displayService.playSound(arena, com.talexck.minigamelib.api.arena.ArenaSound.minecraft(
              org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.5f));
        }
        displayService.tickScoreboards(arena, 0);
        displayService.applyBossBar(arena, 0);
      }
    }.runTaskTimer(plugin, 20L, 20L);
    arena.boundaryTasks().add(task);
  }

  private void sendVictoryFeedback(RuntimeArena arena) {
    com.talexck.minigamelib.api.arena.ArenaPresentation presentation =
        arena.settings().presentation();
    ArenaTeamColor winner = arena.winningTeam();
    for (String playerName : arena.playerNames()) {
      org.bukkit.entity.Player player = Bukkit.getPlayerExact(playerName);
      if (player == null) {
        continue;
      }
      boolean won = winner != null && arena.teamOf(playerName).map(winner::equals).orElse(false);
      displayService.showTitle(player, arena,
          won ? presentation.victoryTitle() : presentation.defeatTitle(),
          won ? presentation.victorySubtitle() : presentation.defeatSubtitle(),
          java.util.Map.of(), java.time.Duration.ofMillis(200), java.time.Duration.ofSeconds(4),
          java.time.Duration.ofMillis(800));
      player.playSound(player.getLocation(), won ? org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE
          : org.bukkit.Sound.ENTITY_WITHER_DEATH, won ? 1.0f : 0.4f, 1.0f);
      if (won && !arena.isFailed(playerName)) {
        launchFirework(player.getLocation(), winner);
      }
    }
  }

  private void launchFirework(org.bukkit.Location location, ArenaTeamColor color) {
    org.bukkit.entity.Firework firework = location.getWorld().spawn(location.clone().add(0, 1, 0),
        org.bukkit.entity.Firework.class, spawned -> {
          org.bukkit.inventory.meta.FireworkMeta meta = spawned.getFireworkMeta();
          meta.addEffect(org.bukkit.FireworkEffect.builder()
              .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
              .withColor(TeamPalette.leather(color)).withFade(org.bukkit.Color.WHITE)
              .trail(true).flicker(true).build());
          meta.setPower(1);
          spawned.setFireworkMeta(meta);
        });
    firework.setPersistent(false);
  }

  private List<ArenaTeam> resolveTeams(ArenaCreateRequest request) {
    ArenaLayout layout = request.layout() != null ? request.layout()
        : templates.get(request.templateId()).defaultLayout();
    ArenaSettings settings = request.settings() != null ? request.settings()
        : templates.get(request.templateId()).defaultSettings();
    List<ArenaTeamColor> configuredColors =
        layout.teamSpawns().stream().map(ArenaTeamSpawn::color).distinct().toList();
    return TeamDistribution.resolveTeams(request.initialPlayerNames(), configuredColors,
        settings.maxTeamSize(), settings.rules().teamFillMode());
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
