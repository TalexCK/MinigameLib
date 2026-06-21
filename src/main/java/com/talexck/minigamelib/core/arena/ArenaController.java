package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaActionBarConfig;
import com.talexck.minigamelib.api.arena.ArenaBossBarConfig;
import com.talexck.minigamelib.api.arena.ArenaCreateRequest;
import com.talexck.minigamelib.api.arena.ArenaGameResult;
import com.talexck.minigamelib.api.arena.ArenaHandle;
import com.talexck.minigamelib.api.arena.ArenaLayout;
import com.talexck.minigamelib.api.arena.ArenaLifecycleListener;
import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.arena.ArenaPlayerStats;
import com.talexck.minigamelib.api.arena.ArenaScoreboardConfig;
import com.talexck.minigamelib.api.arena.ArenaSettings;
import com.talexck.minigamelib.api.arena.ArenaSound;
import com.talexck.minigamelib.api.arena.ArenaSoundConfig;
import com.talexck.minigamelib.api.arena.ArenaStatus;
import com.talexck.minigamelib.api.arena.ArenaStopReason;
import com.talexck.minigamelib.api.arena.ArenaTeam;
import com.talexck.minigamelib.api.arena.ArenaTeamColor;
import com.talexck.minigamelib.api.arena.ArenaTeamSpawn;
import com.talexck.minigamelib.api.arena.ArenaTemplate;
import com.talexck.minigamelib.api.arena.ArenaTitleConfig;
import com.talexck.minigamelib.api.arena.ArenaTitleFrame;
import com.talexck.minigamelib.core.chest.DefaultChestService;
import com.talexck.minigamelib.core.resourcepack.ResourcePackService;
import com.talexck.minigamelib.core.world.DefaultWorldService;
import com.talexck.minigamelib.core.world.WorldCreateRequest;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.bossbar.BarColor;
import me.neznamy.tab.api.bossbar.BarStyle;
import me.neznamy.tab.api.tablist.HeaderFooterManager;
import me.neznamy.tab.api.tablist.layout.Layout;
import me.neznamy.tab.api.tablist.layout.LayoutManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ArenaController implements Listener, ArenaLifecycleControl, ArenaDisplay {

  private static final long POST_GAME_RETURN_DELAY_TICKS = 20L * 15L;
  private static final int TAB_LAYOUT_SIZE = 80;
  private static final int TAB_COLUMN_WIDTH = 28;

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
  private final CombatService combatService;
  private final ItemCombatService itemCombatService;
  private final java.util.Set<String> warnedTabFeatures = ConcurrentHashMap.newKeySet();
  private final BukkitTask lobbyTabRefreshTask;

  public ArenaController(JavaPlugin plugin, DefaultWorldService worldService) {
    this.plugin = plugin;
    this.worldService = worldService;
    this.chestService = new DefaultChestService(plugin);
    this.resourcePackService = new ResourcePackService(plugin);
    this.lootService = new LootService(chestService);
    this.boundaryService = new BoundaryService(plugin, registry, chestService);
    this.itemService = new ItemService(plugin, registry);
    this.combatService = new CombatService(plugin, registry, this::refreshScoreboards, this);
    this.itemCombatService = new ItemCombatService(plugin, registry, itemService, combatService);
    Bukkit.getPluginManager().registerEvents(this, plugin);
    this.lobbyTabRefreshTask = Bukkit.getScheduler().runTaskTimer(plugin,
        this::resetLobbyTabViews, 40L, 40L);
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
        playConfiguredSound(arena, arena.settings().sounds().teleport());
        teleportPlayersToSpawn(arena);
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
        broadcastMessagesNow(arena, arena.settings().messages().gameStopped(), 0, reason);
        sendConfiguredActionBar(arena, arena.settings().actionBar().gameStopped(), 0, reason);
        sendConfiguredTitle(arena, arena.settings().title().gameStopped(), 0, reason);
        playConfiguredSound(arena, arena.settings().sounds().gameStopped());
        setArenaPlayersGameMode(arena, GameMode.SPECTATOR);
        itemService.clearArenaPlayerInventories(arena);
        spawnCageService.clear(arena.arenaId());
        chestService.stopArenaChests(arenaId, false);
        boundaryService.cancelTasks(arena);
        boundaryService.remove(arenaId);
        clearBossBar(arena);
        clearScoreboards(arena);
        ArenaGameResult result = gameResult(arena, reason);
        arena.listener().onGameEnded(arena.handle(), result);
        broadcastFinalTeamRanking(arena);
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

  @Override
  public void refreshScoreboards(RuntimeArena arena, int secondsLeft) {
    applyScoreboards(arena, secondsLeft);
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

  public CompletableFuture<Void> playSound(String arenaId, ArenaSound sound) {
    RuntimeArena arena = requireArena(arenaId);
    CompletableFuture<Void> future = new CompletableFuture<>();

    Bukkit.getScheduler().runTask(plugin, () -> {
      try {
        playSoundNow(arena, sound);
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
    RuntimeArena arena = registry.get(arenaId);
    return arena == null ? Optional.empty() : Optional.of(arena.handle());
  }

  public List<ArenaHandle> arenas() {
    return registry.stream().map(RuntimeArena::handle).toList();
  }

  public void shutdown() {
    lobbyTabRefreshTask.cancel();
    List<String> arenaIds = registry.ids();
    for (String arenaId : arenaIds) {
      RuntimeArena arena = registry.remove(arenaId);
      if (arena != null) {
        chestService.stopArenaChests(arenaId, false);
        boundaryService.cancelTasks(arena);
        boundaryService.remove(arenaId);
        spawnCageService.clear(arena.arenaId());
        clearBossBar(arena);
        worldService.unloadWorld(arena.world().world(), arena.settings().saveWorldOnUnload());
        arena.setStatus(ArenaStatus.DESTROYED);
      }
    }
    HandlerList.unregisterAll(this);
    boundaryService.shutdown();
    itemService.shutdown();
    itemCombatService.shutdown();
    combatService.shutdown();
    resourcePackService.shutdown();
  }

  private void setupWorld(RuntimeArena arena) {
    arena.world().world().setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
    boundaryService.init(arena);
  }

  private void teleportPlayersToSpawn(RuntimeArena arena) {
    World world = arena.world().world();
    Map<ArenaTeamColor, List<ArenaPoint>> teamSpawns = teamSpawnMap(arena.layout().teamSpawns());

    for (ArenaTeam team : arena.teams()) {
      List<ArenaPoint> spawnPoints =
          teamSpawns.getOrDefault(team.color(), arena.layout().spawnPoints());
      spawnCageService.createTeamSpawnCage(arena.arenaId(), world, spawnPoints);
      for (int index = 0; index < team.playerNames().size(); index++) {
        Player player = Bukkit.getPlayerExact(team.playerNames().get(index));
        if (player == null || spawnPoints.isEmpty()) {
          continue;
        }
        ArenaPoint point = spawnPoints.get(index % spawnPoints.size());
        player.teleport(safeSpawnLocation(point.toLocation(world)));
        player.setGameMode(GameMode.ADVENTURE);
        applyPlayerListName(arena, player);
      }
    }
  }

  private Location safeSpawnLocation(Location location) {
    Location adjusted = location.clone();
    for (int attempt = 0; attempt < 4; attempt++) {
      if (adjusted.getBlock().isPassable()
          && adjusted.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) {
        return adjusted;
      }
      adjusted.add(0.0, 1.0, 0.0);
    }
    return adjusted;
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
        player.setGameMode(GameMode.ADVENTURE);
        player.playerListName(Component.text(player.getName()));
      }
    }
    resetLobbyTabViews();
  }

  private void finishStoppedArena(RuntimeArena arena, CompletableFuture<Void> future) {
    try {
      if (arena.status() == ArenaStatus.DESTROYED) {
        future.complete(null);
        return;
      }
      teleportPlayersBack(arena);
      chestService.stopArenaChests(arena.arenaId(), true);
      boundaryService.cancelTasks(arena);
      boundaryService.remove(arena.arenaId());
      spawnCageService.clear(arena.arenaId());
      clearBossBar(arena);
      clearScoreboards(arena);
      arena.setStatus(ArenaStatus.STOPPED);
      registry.remove(arena.arenaId(), arena);
      boolean unloaded = worldService.unloadWorld(arena.world().world(), false);
      arena.setStatus(ArenaStatus.DESTROYED);
      arena.listener().onArenaDestroyed(arena.handle());
      broadcastMessagesNow(arena, arena.settings().messages().destroyed(), 0, null);
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
        applyScoreboards(arena, secondsLeft);
        applyBossBar(arena, secondsLeft);
        arena.listener().onCountdownTick(arena.handle(), secondsLeft);
        String countdownMessage = arena.settings().messages().countdownTick();
        if (!countdownMessage.isBlank()) {
          broadcastMessageNow(arena, countdownMessage, secondsLeft, null);
        }
        sendCountdownTitle(arena, secondsLeft);
        playCountdownSound(arena, secondsLeft);
        sendConfiguredActionBar(arena, arena.settings().actionBar().countdownTick(), secondsLeft,
            null);
        sendConfiguredTitle(arena, arena.settings().title().countdownTick(), secondsLeft, null);
        playConfiguredSound(arena, arena.settings().sounds().countdownTick());
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
    setArenaPlayersGameMode(arena, GameMode.SURVIVAL);
    itemService.startInfiniteBlockMaintenance(arena);
    boundaryService.startLifecycle(arena);
    boundaryService.scheduleStages(arena);
    applyScoreboards(arena, 0);
    applyBossBar(arena, 0);
    arena.listener().onGameStarted(arena.handle());
    broadcastMessagesNow(arena, arena.settings().messages().gameStarted(), 0, null);
    sendConfiguredActionBar(arena, arena.settings().actionBar().gameStarted(), 0, null);
    sendConfiguredTitle(arena, arena.settings().title().gameStarted(), 0, null);
    playConfiguredSound(arena, arena.settings().sounds().gameStarted());
    future.complete(null);
  }

  private void applyScoreboards(RuntimeArena arena, int secondsLeft) {
    applyTabLayout(arena);
    if (applyTabScoreboard(arena, secondsLeft)) {
      return;
    }
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
      Component displayName =
          coloredComponent(renderText(arena, config.title(), secondsLeft, null, playerName));
      Objective objective = scoreboard.registerNewObjective("arena", Criteria.DUMMY, displayName);
      objective.setDisplaySlot(DisplaySlot.SIDEBAR);

      List<String> lines = config.lines();
      for (int index = 0; index < lines.size(); index++) {
        String line = uniqueScoreboardLine(
            legacyColoredText(renderText(arena, lines.get(index), secondsLeft, null, playerName)),
            index);
        objective.getScore(line).setScore(lines.size() - index);
      }
      player.setScoreboard(scoreboard);
    }
  }

  private void clearScoreboards(RuntimeArena arena) {
    clearTabScoreboard(arena);
    resetTabLayout(arena);
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
    if (applyTabBossBar(arena, renderText(arena, config.title(), secondsLeft, null),
        resolveBossBarProgress(arena, secondsLeft), config.color(), config.style())) {
      return;
    }

    BossBar bossBar = arena.bossBar();
    if (bossBar == null) {
      bossBar = Bukkit.createBossBar(
          legacyColoredText(renderText(arena, config.title(), secondsLeft, null)), config.color(),
          config.style());
      arena.setBossBar(bossBar);
    }

    bossBar.setTitle(legacyColoredText(renderText(arena, config.title(), secondsLeft, null)));
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
    ArenaBossBarConfig config = arena.settings().bossBar();
    if (applyTabBossBar(arena, renderText(arena, title, 0, null), progress, config.color(),
        config.style())) {
      return;
    }
    BossBar bossBar = arena.bossBar();
    if (bossBar == null) {
      bossBar = Bukkit.createBossBar(legacyColoredText(renderText(arena, title, 0, null)),
          config.color(), config.style());
      arena.setBossBar(bossBar);
    }

    bossBar.setTitle(legacyColoredText(renderText(arena, title, 0, null)));
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
    clearTabBossBar(arena);
    BossBar bossBar = arena.bossBar();
    if (bossBar != null) {
      bossBar.removeAll();
      arena.setBossBar(null);
    }
  }

  private boolean applyTabScoreboard(RuntimeArena arena, int secondsLeft) {
    ArenaScoreboardConfig config = arena.settings().scoreboard();
    if (!config.enabled()) {
      return false;
    }
    try {
      me.neznamy.tab.api.scoreboard.ScoreboardManager manager =
          TabAPI.getInstance().getScoreboardManager();
      if (manager == null) {
        warnTabFeatureOnce("scoreboard");
        return false;
      }
      String name = "mgl-scoreboard-" + arena.arenaId();
      me.neznamy.tab.api.scoreboard.Scoreboard scoreboard =
          manager.getRegisteredScoreboards().get(name);
      List<String> lines = config.lines().stream()
          .map(line -> legacyColoredText(renderText(arena, line, secondsLeft, null))).toList();
      String title = legacyColoredText(renderText(arena, config.title(), secondsLeft, null));
      if (scoreboard == null) {
        scoreboard = manager.createScoreboard(name, title, lines);
        arena.setTabScoreboardName(name);
      } else {
        scoreboard.setTitle(title);
        scoreboard.setLines(lines);
      }
      for (String playerName : arena.playerNames()) {
        TabPlayer tabPlayer = tabPlayer(playerName);
        if (tabPlayer != null) {
          manager.showScoreboard(tabPlayer, scoreboard);
        }
      }
      return true;
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB scoreboard unavailable: " + exception.getMessage());
      return false;
    }
  }

  private void clearTabScoreboard(RuntimeArena arena) {
    try {
      me.neznamy.tab.api.scoreboard.ScoreboardManager manager =
          TabAPI.getInstance().getScoreboardManager();
      if (manager == null) {
        return;
      }
      for (String playerName : arena.playerNames()) {
        TabPlayer tabPlayer = tabPlayer(playerName);
        if (tabPlayer != null && manager.hasCustomScoreboard(tabPlayer)) {
          manager.resetScoreboard(tabPlayer);
        }
      }
      String name = arena.tabScoreboardName();
      if (name != null && manager.getRegisteredScoreboards().containsKey(name)) {
        manager.removeScoreboard(name);
      }
      arena.setTabScoreboardName(null);
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB scoreboard cleanup skipped: " + exception.getMessage());
    }
  }

  private boolean applyTabBossBar(RuntimeArena arena, String title, double progress,
      org.bukkit.boss.BarColor color, org.bukkit.boss.BarStyle style) {
    try {
      me.neznamy.tab.api.bossbar.BossBarManager manager = TabAPI.getInstance().getBossBarManager();
      if (manager == null) {
        warnTabFeatureOnce("bossbar");
        return false;
      }
      String renderedTitle = legacyColoredText(title);
      float renderedProgress = (float) (clampProgress(progress) * 100.0);
      me.neznamy.tab.api.bossbar.BossBar bossBar = null;
      if (arena.tabBossBarName() != null) {
        bossBar = manager.getBossBar(arena.tabBossBarName());
      }
      if (bossBar == null) {
        bossBar = manager.createBossBar(renderedTitle, renderedProgress, tabBarColor(color),
            tabBarStyle(style));
        arena.setTabBossBarName(bossBar.getName());
      } else {
        bossBar.setTitle(renderedTitle);
        bossBar.setProgress(renderedProgress);
        bossBar.setColor(tabBarColor(color));
        bossBar.setStyle(tabBarStyle(style));
      }
      for (TabPlayer viewer : List.copyOf(bossBar.getPlayers())) {
        if (!arena.playerNames().contains(viewer.getName())) {
          bossBar.removePlayer(viewer);
        }
      }
      for (String playerName : arena.playerNames()) {
        TabPlayer tabPlayer = tabPlayer(playerName);
        if (tabPlayer != null && !bossBar.containsPlayer(tabPlayer)) {
          bossBar.addPlayer(tabPlayer);
        }
      }
      return true;
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB bossbar unavailable: " + exception.getMessage());
      return false;
    }
  }

  private void clearTabBossBar(RuntimeArena arena) {
    try {
      me.neznamy.tab.api.bossbar.BossBarManager manager = TabAPI.getInstance().getBossBarManager();
      String name = arena.tabBossBarName();
      if (manager != null && name != null && manager.getBossBar(name) != null) {
        manager.removeBossBar(name);
      }
      arena.setTabBossBarName(null);
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB bossbar cleanup skipped: " + exception.getMessage());
    }
  }

  private void applyTabLayout(RuntimeArena arena) {
    try {
      TabAPI api = TabAPI.getInstance();
      LayoutManager layoutManager = api.getLayoutManager();
      if (layoutManager == null) {
        warnTabFeatureOnce("layout");
        applyTabHeader(arena);
        return;
      }
      long revision = arena.nextTabLayoutRevision();
      for (String playerName : arena.playerNames()) {
        TabPlayer tabPlayer = tabPlayer(playerName);
        if (tabPlayer != null) {
          String layoutName = "mgl-layout-" + arena.arenaId() + "-" + revision + "-"
              + playerName.toLowerCase(java.util.Locale.ROOT);
          Layout layout = layoutManager.createNewLayout(layoutName, TAB_LAYOUT_SIZE);
          fillTabPanelBackground(layout);
          fillTeamColumns(layout, arena);
          fillTeamStatsColumn(layout, arena);
          fillPersonalStatsColumn(layout, arena, playerName);
          layoutManager.sendLayout(tabPlayer, layout);
        }
      }
      applyTabHeader(arena);
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB layout unavailable: " + exception.getMessage());
    }
  }

  private void fillTeamColumns(Layout layout, RuntimeArena arena) {
    List<ArenaTeamColor> colors = tabTeamColors(arena);
    for (int index = 0; index < colors.size(); index++) {
      int column = index / 4;
      if (column > 1) {
        break;
      }
      int row = index % 4;
      int baseSlot = column * 20 + row * 5 + 1;
      ArenaTeamColor color = colors.get(index);
      ArenaTeam team = teamByColor(arena, color).orElse(new ArenaTeam(color, List.of()));
      addTabSlot(layout, baseSlot, tabColorCode(color) + teamDisplayName(color) + " - "
          + alivePlayers(arena, team) + "/" + team.playerNames().size() + "人");
      for (int offset = 0; offset < 4; offset++) {
        String playerName =
            offset < team.playerNames().size() ? team.playerNames().get(offset) : "";
        addTabSlot(layout, baseSlot + offset + 1,
            playerName.isBlank() ? "&r" : tabColorCode(color) + playerName);
      }
    }
  }

  private void fillTabPanelBackground(Layout layout) {
    for (int slot = 1; slot <= TAB_LAYOUT_SIZE; slot++) {
      addTabSlot(layout, slot, "&8" + " ".repeat(TAB_COLUMN_WIDTH));
    }
  }

  private void fillTeamStatsColumn(Layout layout, RuntimeArena arena) {
    addTabSlot(layout, 41, "&e&l队伍排名");
    long now = System.currentTimeMillis();
    List<ArenaTeam> rankedTeams = arena.teams().stream()
        .sorted(java.util.Comparator.comparingInt((ArenaTeam team) -> arena.teamScore(team, now))
            .reversed().thenComparing(team -> team.color().ordinal()))
        .toList();
    int slot = 42;
    int rank = 1;
    for (ArenaTeam team : rankedTeams) {
      if (slot > 60) {
        break;
      }
      ArenaTeamColor color = team.color();
      addTabSlot(layout, slot++, "&f#" + rank++ + " " + tabColorCode(color) + teamDisplayName(color)
          + " &e" + arena.teamScore(team, now));
      if (slot <= 60) {
        addTabSlot(layout, slot++,
            "&7击杀 &c" + arena.teamKills(team) + " &7存活秒 &a" + arena.teamSurvivalSeconds(team, now));
      }
    }
  }

  private void fillPersonalStatsColumn(Layout layout, RuntimeArena arena, String playerName) {
    ArenaPlayerStats stats = playerStats(arena, playerName).orElse(null);
    ArenaTeamColor color = stats == null ? null : stats.teamColor();
    String colorCode = color == null ? "&f" : tabColorCode(color);
    String teamName = color == null ? "无队伍" : teamDisplayName(color);
    boolean failed = stats != null && stats.failed();
    addTabSlot(layout, 61, "&b&l个人统计");
    addTabSlot(layout, 62, colorCode + playerName);
    addTabSlot(layout, 63, "&7队伍 " + colorCode + teamName);
    addTabSlot(layout, 64, "&7击杀 &c" + (stats == null ? 0 : stats.kills()));
    addTabSlot(layout, 65, "&7死亡 &f" + (stats == null ? 0 : stats.deaths()));
    addTabSlot(layout, 66, "&7状态 " + (failed ? "&c淘汰" : "&a存活"));
  }

  private void addTabSlot(Layout layout, int slot, String text) {
    layout.addFixedSlot(slot, widenTabText(text), 1);
  }

  private String widenTabText(String text) {
    String safeText = text == null ? "" : text;
    int visibleLength = visibleTabLength(safeText);
    int padding = Math.max(2, TAB_COLUMN_WIDTH - visibleLength);
    return safeText + "&0" + " ".repeat(padding);
  }

  private int visibleTabLength(String text) {
    return TextRender.visibleLength(text);
  }

  private void resetTabLayout(RuntimeArena arena) {
    try {
      TabAPI api = TabAPI.getInstance();
      LayoutManager layoutManager = api.getLayoutManager();
      HeaderFooterManager headerFooterManager = api.getHeaderFooterManager();
      for (String playerName : arena.playerNames()) {
        TabPlayer tabPlayer = tabPlayer(playerName);
        if (tabPlayer == null) {
          continue;
        }
        sendLobbyTabLayout(layoutManager, tabPlayer);
        if (headerFooterManager != null) {
          headerFooterManager.setHeaderAndFooter(tabPlayer, "", "");
        }
      }
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB layout cleanup skipped: " + exception.getMessage());
    }
  }

  private void resetTabView(Player player) {
    try {
      TabAPI api = TabAPI.getInstance();
      TabPlayer tabPlayer = tabPlayer(player.getName());
      if (tabPlayer == null) {
        return;
      }
      LayoutManager layoutManager = api.getLayoutManager();
      HeaderFooterManager headerFooterManager = api.getHeaderFooterManager();
      sendLobbyTabLayout(layoutManager, tabPlayer);
      if (headerFooterManager != null) {
        headerFooterManager.setHeaderAndFooter(tabPlayer, "", "");
      }
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB reset skipped: " + exception.getMessage());
    }
  }

  private void resetLobbyTabViews() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (isInActiveArena(player.getName())) {
        continue;
      }
      resetTabView(player);
    }
  }

  private boolean isInActiveArena(String playerName) {
    return registry.isInActiveArena(playerName);
  }

  private void sendLobbyTabLayout(LayoutManager layoutManager, TabPlayer tabPlayer) {
    if (layoutManager == null || tabPlayer == null) {
      return;
    }
    List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
    onlinePlayers.sort(
        java.util.Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
    int layoutSize = Math.max(1, Math.min(TAB_LAYOUT_SIZE, onlinePlayers.size()));
    Layout layout = layoutManager.createNewLayout(
        "mgl-lobby-" + tabPlayer.getUniqueId() + "-" + System.nanoTime(), layoutSize);
    int slot = 1;
    for (Player onlinePlayer : onlinePlayers) {
      if (slot > layoutSize) {
        break;
      }
      layout.addFixedSlot(slot++, onlinePlayer.getName(), 1);
    }
    layoutManager.sendLayout(tabPlayer, layout);
  }

  private void applyTabHeader(RuntimeArena arena) {
    try {
      HeaderFooterManager manager = TabAPI.getInstance().getHeaderFooterManager();
      if (manager == null) {
        warnTabFeatureOnce("header-footer");
        return;
      }
      String header = "§b§l" + gamePluginName() + " §7v" + gamePluginVersion()
          + "\n§a§lMinigameLib §7v" + plugin.getPluginMeta().getVersion();
      for (String playerName : arena.playerNames()) {
        TabPlayer tabPlayer = tabPlayer(playerName);
        if (tabPlayer != null) {
          manager.setHeaderAndFooter(tabPlayer, header, "");
        }
      }
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB header unavailable: " + exception.getMessage());
    }
  }

  private TabPlayer tabPlayer(String playerName) {
    try {
      TabPlayer player = TabAPI.getInstance().getPlayer(playerName);
      return player != null && player.isLoaded() ? player : null;
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private void warnTabFeatureOnce(String feature) {
    if (warnedTabFeatures.add(feature)) {
      plugin.getLogger().warning(
          "TAB " + feature + " manager 不可用，请确认 plugins/TAB/config.yml 中对应功能已启用并已 /tab reload。");
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
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        String rendered = renderText(arena, message, secondsLeft, reason, playerName);
        player.sendMessage(coloredComponent(rendered));
      }
    }
  }

  private void broadcastFinalTeamRanking(RuntimeArena arena) {
    long now = System.currentTimeMillis();
    List<ArenaTeamColor> ranking = arena.finalTeamRanking();
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null) {
        continue;
      }
      player.sendMessage(coloredComponent("&b&lSky Battle &7最终队伍排名"));
      for (int index = 0; index < ranking.size(); index++) {
        ArenaTeamColor color = ranking.get(index);
        ArenaTeam team = teamByColor(arena, color).orElse(new ArenaTeam(color, List.of()));
        String line = "&f#" + (index + 1) + " " + tabColorCode(color) + teamDisplayName(color)
            + " &7- " + finalRankingPlayers(team, color)
            + " &7积分 &e" + arena.teamScore(team, now) + " &7击杀 &c" + arena.teamKills(team)
            + " &7存活秒 &a" + arena.teamSurvivalSeconds(team, now);
        player.sendMessage(coloredComponent(line));
      }
    }
  }

  private String finalRankingPlayers(ArenaTeam team, ArenaTeamColor color) {
    if (team.playerNames().isEmpty()) {
      return "&7无玩家";
    }
    String colorCode = tabColorCode(color);
    return colorCode + String.join("&7, " + colorCode, team.playerNames());
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
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        Component rendered =
            coloredComponent(renderText(arena, message, secondsLeft, reason, playerName));
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
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        Title rendered = Title.title(
            coloredComponent(renderText(arena, frame.title(), secondsLeft, reason, playerName)),
            coloredComponent(renderText(arena, frame.subtitle(), secondsLeft, reason, playerName)),
            Title.Times.times(frame.fadeIn(), frame.stay(), frame.fadeOut()));
        player.showTitle(rendered);
      }
    }
  }

  private void sendCountdownTitle(RuntimeArena arena, int secondsLeft) {
    ArenaTitleFrame frame = new ArenaTitleFrame("&e&l%seconds%", "", java.time.Duration.ZERO,
        java.time.Duration.ofMillis(900), java.time.Duration.ZERO);
    sendTitleNow(arena, frame, secondsLeft, null);
  }

  private void playConfiguredSound(RuntimeArena arena, ArenaSound sound) {
    ArenaSoundConfig config = arena.settings().sounds();
    if (!config.enabled() || sound == null) {
      return;
    }
    playSoundNow(arena, sound);
  }

  private void playSoundNow(RuntimeArena arena, ArenaSound sound) {
    if (sound == null) {
      return;
    }
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null) {
        continue;
      }
      if (sound.minecraftSound() != null) {
        player.playSound(player.getLocation(), sound.minecraftSound(), sound.category(),
            sound.volume(), sound.pitch());
      } else {
        player.playSound(player.getLocation(), sound.customSound(), sound.category(),
            sound.volume(), sound.pitch());
      }
    }
  }

  private void playCountdownSound(RuntimeArena arena, int secondsLeft) {
    Sound sound = secondsLeft <= 0 ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_PLING;
    float pitch = secondsLeft <= 0 ? 1.1f : 1.75f;
    playSoundNow(arena, ArenaSound.minecraft(sound, 0.8f, pitch));
  }

  private void sendAvailableResourcePacks(Player player) {
    List<ArenaSettings> settings = new ArrayList<>();
    registry.stream().map(RuntimeArena::settings).forEach(settings::add);
    templates.values().stream().map(ArenaTemplate::defaultSettings).forEach(settings::add);
    for (ArenaSettings setting : settings) {
      if (setting.resourcePack().enabled()) {
        resourcePackService.sendResourcePack(player, setting.resourcePack());
      }
    }
  }

  private String renderText(RuntimeArena arena, String text, int secondsLeft,
      ArenaStopReason reason) {
    return renderText(arena, text, secondsLeft, reason, null);
  }

  private String renderText(RuntimeArena arena, String text, int secondsLeft,
      ArenaStopReason reason, String playerName) {
    String team = playerName == null ? "" : arena.teamOf(playerName).map(Enum::name).orElse("");
    String kills = playerName == null ? "0"
        : arena.playerStats().stream().filter(stats -> stats.playerName().equals(playerName))
            .findFirst().map(stats -> Integer.toString(stats.kills())).orElse("0");
    String deaths = playerName == null ? "0"
        : arena.playerStats().stream().filter(stats -> stats.playerName().equals(playerName))
            .findFirst().map(stats -> Integer.toString(stats.deaths())).orElse("0");
    String countdown = Integer.toString(Math.max(0, secondsLeft));
    Map<String, String> placeholders = new java.util.LinkedHashMap<>();
    placeholders.put("{arena}", arena.arenaId());
    placeholders.put("{template}", arena.templateId());
    placeholders.put("{world}", arena.world().runtimeWorldName());
    placeholders.put("{status}", arena.status().name());
    placeholders.put("{players}", Integer.toString(arena.playerNames().size()));
    placeholders.put("{aliveTeams}", Long.toString(arena.teams().stream()
        .filter(teamValue -> !arena.isTeamFailed(teamValue.color())).count()));
    placeholders.put("{winner}", arena.winningTeam() == null ? "" : arena.winningTeam().name());
    placeholders.put("{team}", team);
    placeholders.put("{kills}", kills);
    placeholders.put("{deaths}", deaths);
    placeholders.put("{countdown}", countdown);
    placeholders.put("%seconds%", countdown);
    placeholders.put("{reason}", reason == null ? "" : reason.name());
    return TextRender.render(text, placeholders);
  }

  private Component coloredComponent(String text) {
    return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
  }

  private String legacyColoredText(String text) {
    return LegacyComponentSerializer.legacySection()
        .serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(text));
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

  @EventHandler
  public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
    event.message(null);
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    Bukkit.getScheduler().runTask(plugin, () -> {
      event.getPlayer().setGameMode(GameMode.ADVENTURE);
      resetLobbyTabViews();
      sendAvailableResourcePacks(event.getPlayer());
    });
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    resourcePackService.clearPlayer(event.getPlayer().getUniqueId());
    Bukkit.getScheduler().runTask(plugin, this::resetLobbyTabViews);
  }

  @SuppressWarnings("deprecation")
  private void setArenaPlayersGameMode(RuntimeArena arena, GameMode gameMode) {
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        player.setGameMode(gameMode);
      }
    }
  }

  private void applyPlayerListName(RuntimeArena arena, Player player) {
    NamedTextColor color =
        arena.teamOf(player.getName()).map(this::tabColor).orElse(NamedTextColor.WHITE);
    player.playerListName(Component.text(player.getName(), color));
  }

  private NamedTextColor tabColor(ArenaTeamColor color) {
    return TeamPalette.textColor(color);
  }

  private String tabColorCode(ArenaTeamColor color) {
    return TeamPalette.legacyCode(color);
  }

  private String teamDisplayName(ArenaTeamColor color) {
    return switch (color) {
      case RED -> "红队";
      case YELLOW -> "黄队";
      case GREEN -> "绿队";
      case BLUE -> "蓝队";
      case ORANGE -> "橙队";
      case PURPLE -> "紫队";
      case WHITE -> "白队";
      case PINK -> "粉队";
      case GRAY -> "灰队";
      case CYAN -> "青队";
    };
  }

  private BarColor tabBarColor(org.bukkit.boss.BarColor color) {
    return TeamPalette.tabBarColor(color);
  }

  private BarStyle tabBarStyle(org.bukkit.boss.BarStyle style) {
    return TeamPalette.tabBarStyle(style);
  }

  private List<ArenaTeamColor> tabTeamColors(RuntimeArena arena) {
    List<ArenaTeamColor> configured =
        arena.layout().teamSpawns().stream().map(ArenaTeamSpawn::color).distinct().toList();
    if (!configured.isEmpty()) {
      return configured;
    }
    return arena.teams().stream().map(ArenaTeam::color).distinct().toList();
  }

  private Optional<ArenaTeam> teamByColor(RuntimeArena arena, ArenaTeamColor color) {
    return arena.teams().stream().filter(team -> team.color() == color).findFirst();
  }

  private long alivePlayers(RuntimeArena arena, ArenaTeam team) {
    return team.playerNames().stream().filter(playerName -> !arena.isFailed(playerName)).count();
  }

  private Optional<ArenaPlayerStats> playerStats(RuntimeArena arena, String playerName) {
    return arena.playerStats().stream()
        .filter(stats -> stats.playerName().equalsIgnoreCase(playerName)).findFirst();
  }

  private String gamePluginName() {
    org.bukkit.plugin.Plugin skyBattle = Bukkit.getPluginManager().getPlugin("SkyBattle");
    return skyBattle == null ? "SkyBattle" : skyBattle.getPluginMeta().getName();
  }

  private String gamePluginVersion() {
    org.bukkit.plugin.Plugin skyBattle = Bukkit.getPluginManager().getPlugin("SkyBattle");
    return skyBattle == null ? "unknown" : skyBattle.getPluginMeta().getVersion();
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

  private Map<ArenaTeamColor, List<ArenaPoint>> teamSpawnMap(List<ArenaTeamSpawn> spawns) {
    return TeamDistribution.teamSpawnMap(spawns);
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
