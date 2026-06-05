package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaActionBarConfig;
import com.talexck.minigamelib.api.arena.ArenaBoundaryStage;
import com.talexck.minigamelib.api.arena.ArenaBoundaryWall;
import com.talexck.minigamelib.api.arena.ArenaBossBarConfig;
import com.talexck.minigamelib.api.arena.ArenaCreateRequest;
import com.talexck.minigamelib.api.arena.ArenaGameResult;
import com.talexck.minigamelib.api.arena.ArenaHandle;
import com.talexck.minigamelib.api.arena.ArenaItemEntry;
import com.talexck.minigamelib.api.arena.ArenaItemMode;
import com.talexck.minigamelib.api.arena.ArenaLayout;
import com.talexck.minigamelib.api.arena.ArenaLifecycleListener;
import com.talexck.minigamelib.api.arena.ArenaLootChest;
import com.talexck.minigamelib.api.arena.ArenaLootEntry;
import com.talexck.minigamelib.api.arena.ArenaLootPlacementMode;
import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.arena.ArenaPotionItemConfig;
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
import com.talexck.minigamelib.core.chest.ChestDefinition;
import com.talexck.minigamelib.core.chest.ChestLootEntry;
import com.talexck.minigamelib.core.chest.ChestPlacementMode;
import com.talexck.minigamelib.core.chest.ChestPosition;
import com.talexck.minigamelib.core.chest.DefaultChestService;
import com.talexck.minigamelib.core.resourcepack.ResourcePackService;
import com.talexck.minigamelib.core.world.DefaultWorldService;
import com.talexck.minigamelib.core.world.WorldCreateRequest;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ArenaController implements Listener {

  private final JavaPlugin plugin;
  private final DefaultWorldService worldService;
  private final DefaultChestService chestService;
  private final ResourcePackService resourcePackService;
  private final ConcurrentMap<Integer, ActivePotionProjectile> potionProjectiles =
      new ConcurrentHashMap<>();
  private final java.util.Set<String> infinitePlacedBlocks = ConcurrentHashMap.newKeySet();
  private final ConcurrentMap<String, ArenaTemplate> templates = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, RuntimeArena> arenas = new ConcurrentHashMap<>();

  public ArenaController(JavaPlugin plugin, DefaultWorldService worldService) {
    this.plugin = plugin;
    this.worldService = worldService;
    this.chestService = new DefaultChestService(plugin);
    this.resourcePackService = new ResourcePackService(plugin);
    Bukkit.getPluginManager().registerEvents(this, plugin);
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
          layout, settings, resolveTeams(request), listener);
      RuntimeArena previous = arenas.putIfAbsent(request.arenaId(), arena);
      if (previous != null) {
        throw new IllegalStateException("Arena already exists: " + request.arenaId());
      }
      setupWorld(arena);
      startLootLifecycle(arena);
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
        sendResourcePack(arena);
        playConfiguredSound(arena, arena.settings().sounds().teleport());
        teleportPlayersToSpawn(arena);
        giveBeginningItems(arena);
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
        playConfiguredSound(arena, arena.settings().sounds().gameStopped());
        teleportPlayersBack(arena);
        chestService.stopArenaChests(arenaId, false);
        cancelBoundaryTasks(arena);
        clearBossBar(arena);
        clearScoreboards(arena);
        ArenaGameResult result = gameResult(arena, reason);
        arena.listener().onGameEnded(arena.handle(), result);
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
      chestService.stopArenaChests(arenaId, true);
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
        chestService.stopArenaChests(arenaId, false);
        cancelBoundaryTasks(arena);
        clearBossBar(arena);
        worldService.unloadWorld(arena.world().world(), arena.settings().saveWorldOnUnload());
        arena.setStatus(ArenaStatus.DESTROYED);
      }
    }
    HandlerList.unregisterAll(this);
    resourcePackService.shutdown();
  }

  private void setupWorld(RuntimeArena arena) {
    World world = arena.world().world();
    ArenaBoundaryWall wall = arena.settings().initialBoundaryWall();
    if (wall != null) {
      world.getWorldBorder().setCenter(wall.centerX(), wall.centerZ());
      world.getWorldBorder().setSize(wall.size());
      return;
    }
    ArenaPoint center = arena.layout().center();
    world.getWorldBorder().setCenter(center.x(), center.z());
    world.getWorldBorder().setSize(arena.layout().initialBorderRadius() * 2);
  }

  private void startLootLifecycle(RuntimeArena arena) {
    List<ChestDefinition> definitions =
        arena.settings().lootChests().isEmpty() ? defaultLootChestDefinitions(arena)
            : arena.settings().lootChests().stream().map(this::toChestDefinition).toList();

    chestService.startArenaChests(arena.arenaId(), arena.world().world(), definitions,
        (definition, location) -> {
          ChestPosition position = definition.position();
          ArenaPoint point = new ArenaPoint(position.x(), position.y(), position.z(), 0f, 0f);
          arena.listener().onLootChestGenerated(arena.handle(), point, location);
        });
  }

  private List<ChestDefinition> defaultLootChestDefinitions(RuntimeArena arena) {
    return arena.layout().lootChestPoints().stream()
        .map(point -> new ChestDefinition(new ChestPosition(point.x(), point.y(), point.z()),
            List.of(), ChestPlacementMode.AUTO, false, false, 0L, 0L))
        .toList();
  }

  private ChestDefinition toChestDefinition(ArenaLootChest chest) {
    ArenaPoint point = chest.position();
    return new ChestDefinition(new ChestPosition(point.x(), point.y(), point.z()),
        chest.lootTable().stream().map(this::toChestLootEntry).toList(),
        toChestPlacementMode(chest.placementMode()), chest.timedRegeneration(),
        chest.timedDestruction(), chest.regenerationPeriodTicks(), chest.destructionDelayTicks());
  }

  private ChestLootEntry toChestLootEntry(ArenaLootEntry entry) {
    return new ChestLootEntry(entry.item(), entry.weight(), entry.earliestGenerationRound());
  }

  private ChestPlacementMode toChestPlacementMode(ArenaLootPlacementMode mode) {
    return switch (mode) {
      case AUTO -> ChestPlacementMode.AUTO;
      case CENTER -> ChestPlacementMode.CENTER;
      case MIRRORED -> ChestPlacementMode.MIRRORED;
    };
  }

  private void teleportPlayersToSpawn(RuntimeArena arena) {
    World world = arena.world().world();
    Map<ArenaTeamColor, List<ArenaPoint>> teamSpawns = teamSpawnMap(arena.layout().teamSpawns());

    for (ArenaTeam team : arena.teams()) {
      List<ArenaPoint> spawnPoints =
          teamSpawns.getOrDefault(team.color(), arena.layout().spawnPoints());
      for (int index = 0; index < team.playerNames().size(); index++) {
        Player player = Bukkit.getPlayerExact(team.playerNames().get(index));
        if (player == null || spawnPoints.isEmpty()) {
          continue;
        }
        ArenaPoint point = spawnPoints.get(index % spawnPoints.size());
        player.teleport(point.toLocation(world));
      }
    }
  }

  private void giveBeginningItems(RuntimeArena arena) {
    if (arena.settings().beginningItems().isEmpty()) {
      return;
    }
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null) {
        continue;
      }
      for (ArenaItemEntry entry : arena.settings().beginningItems()) {
        player.getInventory().addItem(createArenaItemStack(arena, playerName, entry));
      }
    }
  }

  private ItemStack createArenaItemStack(RuntimeArena arena, String playerName,
      ArenaItemEntry entry) {
    ItemStack stack = entry.createStack();
    if (entry.mode() == ArenaItemMode.INFINITE) {
      Material material =
          arena.teamOf(playerName).map(this::concreteMaterial).orElse(Material.WHITE_CONCRETE);
      stack = new ItemStack(material, 64);
      applyItemName(stack, entry.name());
    }
    return stack;
  }

  private void applyItemName(ItemStack stack, String name) {
    if (name == null || name.isBlank()) {
      return;
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta != null) {
      meta.displayName(Component.text(name));
      stack.setItemMeta(meta);
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
    scheduleBoundaryStages(arena);
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
          Component.text(renderText(arena, config.title(), secondsLeft, null, playerName));
      Objective objective = scoreboard.registerNewObjective("arena", Criteria.DUMMY, displayName);
      objective.setDisplaySlot(DisplaySlot.SIDEBAR);

      List<String> lines = config.lines();
      for (int index = 0; index < lines.size(); index++) {
        String line = uniqueScoreboardLine(
            renderText(arena, lines.get(index), secondsLeft, null, playerName), index);
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
      bossBar =
          Bukkit.createBossBar(renderText(arena, title, 0, null), config.color(), config.style());
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
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        String rendered = renderText(arena, message, secondsLeft, reason, playerName);
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
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        Component rendered =
            Component.text(renderText(arena, message, secondsLeft, reason, playerName));
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
            Component.text(renderText(arena, frame.title(), secondsLeft, reason, playerName)),
            Component.text(renderText(arena, frame.subtitle(), secondsLeft, reason, playerName)),
            Title.Times.times(frame.fadeIn(), frame.stay(), frame.fadeOut()));
        player.showTitle(rendered);
      }
    }
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

  private void sendResourcePack(RuntimeArena arena) {
    if (!arena.settings().resourcePack().enabled()) {
      return;
    }
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        resourcePackService.sendResourcePack(player, arena.settings().resourcePack());
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
    return text.replace("{arena}", arena.arenaId()).replace("{template}", arena.templateId())
        .replace("{world}", arena.world().runtimeWorldName())
        .replace("{status}", arena.status().name())
        .replace("{players}", Integer.toString(arena.playerNames().size()))
        .replace("{aliveTeams}",
            Long.toString(arena.teams().stream()
                .filter(teamValue -> !arena.isTeamFailed(teamValue.color())).count()))
        .replace("{winner}", arena.winningTeam() == null ? "" : arena.winningTeam().name())
        .replace("{team}", team).replace("{kills}", kills).replace("{deaths}", deaths)
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

  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();
    RuntimeArena arena = findRunningArenaByPlayer(player.getName()).orElse(null);
    if (arena == null) {
      return;
    }

    Player killer = player.getKiller();
    if (killer != null && arena.playerNames().contains(killer.getName())) {
      arena.recordKill(killer.getName());
    }
    arena.recordDeath(player.getName());
    applyScoreboards(arena, 0);
    checkVictory(arena);
  }

  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    RuntimeArena arena = findArenaByPlayer(player.getName()).orElse(null);
    if (arena == null) {
      return;
    }
    ItemStack hand = event.getItemInHand();
    if (findItemEntry(arena, hand, ArenaItemMode.INFINITE).isEmpty()) {
      return;
    }
    infinitePlacedBlocks.add(blockKey(event.getBlockPlaced()));
    Bukkit.getScheduler().runTask(plugin, () -> refillInfiniteItem(arena, player));
  }

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    String key = blockKey(event.getBlock());
    if (infinitePlacedBlocks.remove(key)) {
      event.setDropItems(false);
    }
  }

  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_AIR
        && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
      return;
    }
    Player player = event.getPlayer();
    RuntimeArena arena = findArenaByPlayer(player.getName()).orElse(null);
    if (arena == null) {
      return;
    }
    ItemStack item = event.getItem();
    ArenaItemEntry selfPotion =
        findItemEntry(arena, item, ArenaItemMode.SELF_POTION).orElse(null);
    if (selfPotion != null) {
      event.setCancelled(true);
      applySelfPotion(player, selfPotion);
      consumeOne(item);
      return;
    }

    ArenaItemEntry entry = findItemEntry(arena, item, ArenaItemMode.POTION).orElse(null);
    if (entry == null) {
      return;
    }
    event.setCancelled(true);
    launchPotionFireball(arena, player, entry);
    consumeOne(item);
  }

  @EventHandler
  public void onProjectileHit(ProjectileHitEvent event) {
    ActivePotionProjectile active = potionProjectiles.remove(event.getEntity().getEntityId());
    if (active == null) {
      return;
    }
    Location location = event.getEntity().getLocation();
    event.getEntity().remove();
    startPotionSphere(active.arenaId(), location, active.config());
  }

  private void checkVictory(RuntimeArena arena) {
    if (arena.settings().victoryCondition() == null) {
      return;
    }
    arena.singleAliveTeam().ifPresent(winner -> {
      arena.setWinningTeam(winner);
      stopArena(arena.arenaId(), ArenaStopReason.NORMAL);
    });
  }

  private Optional<RuntimeArena> findRunningArenaByPlayer(String playerName) {
    return arenas.values().stream().filter(arena -> arena.status() == ArenaStatus.RUNNING)
        .filter(arena -> arena.playerNames().contains(playerName)).findFirst();
  }

  private Optional<RuntimeArena> findArenaByPlayer(String playerName) {
    return arenas.values().stream().filter(
        arena -> arena.status() == ArenaStatus.COUNTDOWN || arena.status() == ArenaStatus.RUNNING)
        .filter(arena -> arena.playerNames().contains(playerName)).findFirst();
  }

  private Optional<ArenaItemEntry> findItemEntry(RuntimeArena arena, ItemStack stack,
      ArenaItemMode mode) {
    if (stack == null || stack.getType().isAir()) {
      return Optional.empty();
    }
    return allConfiguredItems(arena).stream().filter(entry -> entry.mode() == mode)
        .filter(entry -> matchesArenaItem(arena, stack, entry)).findFirst();
  }

  private List<ArenaItemEntry> allConfiguredItems(RuntimeArena arena) {
    List<ArenaItemEntry> items = new ArrayList<>(arena.settings().beginningItems());
    arena.settings().lootChests().stream().flatMap(chest -> chest.lootTable().stream())
        .map(ArenaLootEntry::item).forEach(items::add);
    return items;
  }

  private boolean matchesArenaItem(RuntimeArena arena, ItemStack stack, ArenaItemEntry entry) {
    if (entry.mode() == ArenaItemMode.INFINITE) {
      return isConcrete(stack.getType());
    }
    return stack.getType() == entry.item().getType();
  }

  private void refillInfiniteItem(RuntimeArena arena, Player player) {
    ArenaItemEntry entry = arena.settings().beginningItems().stream()
        .filter(item -> item.mode() == ArenaItemMode.INFINITE).findFirst().orElse(null);
    if (entry == null) {
      return;
    }
    Material material =
        arena.teamOf(player.getName()).map(this::concreteMaterial).orElse(Material.WHITE_CONCRETE);
    for (ItemStack stack : player.getInventory().getContents()) {
      if (stack != null && stack.getType() == material) {
        stack.setAmount(64);
        return;
      }
    }
    player.getInventory().addItem(createArenaItemStack(arena, player.getName(), entry));
  }

  private void launchPotionFireball(RuntimeArena arena, Player player, ArenaItemEntry entry) {
    SmallFireball fireball = player.launchProjectile(SmallFireball.class);
    fireball.setIsIncendiary(false);
    fireball.setYield(0);
    fireball.setVelocity(player.getLocation().getDirection().normalize().multiply(1.8));
    potionProjectiles.put(fireball.getEntityId(),
        new ActivePotionProjectile(arena.arenaId(), entry.potionConfig()));
  }

  private void applySelfPotion(Player player, ArenaItemEntry entry) {
    ArenaPotionItemConfig config = entry.potionConfig();
    player.addPotionEffect(new PotionEffect(config.effectType(),
        Math.max(1, (int) toTicks(config.effectDuration())), config.amplifier(), true, true, true));
  }

  private void startPotionSphere(String arenaId, Location center, ArenaPotionItemConfig config) {
    long durationTicks = toTicks(config.duration());
    long effectTicks = Math.max(1L, toTicks(config.effectDuration()));
    new BukkitRunnable() {
      private long elapsedTicks;

      @Override
      public void run() {
        RuntimeArena arena = arenas.get(arenaId);
        if (arena == null || elapsedTicks > durationTicks) {
          cancel();
          return;
        }
        double radiusSquared = config.radius() * config.radius();
        for (String playerName : arena.playerNames()) {
          Player player = Bukkit.getPlayerExact(playerName);
          if (player != null && player.getWorld().equals(center.getWorld())
              && player.getLocation().distanceSquared(center) <= radiusSquared) {
            player.addPotionEffect(new PotionEffect(config.effectType(), (int) effectTicks,
                config.amplifier(), true, true, true));
          }
        }
        elapsedTicks += 20L;
      }
    }.runTaskTimer(plugin, 0L, 20L);
  }

  private void consumeOne(ItemStack item) {
    if (item.getAmount() <= 1) {
      item.setAmount(0);
      return;
    }
    item.setAmount(item.getAmount() - 1);
  }

  private String blockKey(Block block) {
    return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
  }

  private boolean isConcrete(Material material) {
    return material.name().endsWith("_CONCRETE");
  }

  private Material concreteMaterial(ArenaTeamColor color) {
    return switch (color) {
      case RED -> Material.RED_CONCRETE;
      case YELLOW -> Material.YELLOW_CONCRETE;
      case GREEN -> Material.GREEN_CONCRETE;
      case BLUE -> Material.BLUE_CONCRETE;
      case ORANGE -> Material.ORANGE_CONCRETE;
      case PURPLE -> Material.PURPLE_CONCRETE;
      case WHITE -> Material.WHITE_CONCRETE;
      case PINK -> Material.PINK_CONCRETE;
      case GRAY -> Material.GRAY_CONCRETE;
      case CYAN -> Material.CYAN_CONCRETE;
    };
  }

  private List<ArenaTeam> resolveTeams(ArenaCreateRequest request) {
    if (!request.initialTeams().isEmpty()) {
      return request.initialTeams();
    }

    ArenaTeamColor[] colors = ArenaTeamColor.values();
    List<ArenaTeam> teams = new ArrayList<>();
    for (int index = 0; index < request.initialPlayerNames().size(); index++) {
      ArenaTeamColor color = colors[index % colors.length];
      teams.add(new ArenaTeam(color, List.of(request.initialPlayerNames().get(index))));
    }
    return teams;
  }

  private Map<ArenaTeamColor, List<ArenaPoint>> teamSpawnMap(List<ArenaTeamSpawn> spawns) {
    Map<ArenaTeamColor, List<ArenaPoint>> map = new EnumMap<>(ArenaTeamColor.class);
    for (ArenaTeamSpawn spawn : spawns) {
      map.put(spawn.color(), spawn.spawnPoints());
    }
    return map;
  }

  private void scheduleBoundaryStages(RuntimeArena arena) {
    cancelBoundaryTasks(arena);
    long delayTicks = 0L;
    for (ArenaBoundaryStage stage : arena.settings().boundaryStages()) {
      delayTicks += toTicks(stage.delayAfterPreviousStage());
      long durationTicks = toTicks(stage.duration());
      BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
        World world = arena.world().world();
        ArenaPoint center = arena.layout().center();
        world.getWorldBorder().setCenter(center.x(), center.z());
        world.getWorldBorder().changeSize(stage.borderSize(), Math.max(0L, durationTicks / 20L));
      }, delayTicks);
      arena.boundaryTasks().add(task);
      delayTicks += durationTicks;
    }
  }

  private void cancelBoundaryTasks(RuntimeArena arena) {
    arena.boundaryTasks().forEach(BukkitTask::cancel);
    arena.boundaryTasks().clear();
  }

  private long toTicks(java.time.Duration duration) {
    return Math.max(0L, duration.toMillis() / 50L);
  }

  private ArenaGameResult gameResult(RuntimeArena arena, ArenaStopReason reason) {
    return new ArenaGameResult(arena.arenaId(), arena.winningTeam(), arena.teamStats(),
        arena.playerStats(), reason);
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

  private record ActivePotionProjectile(String arenaId, ArenaPotionItemConfig config) {
  }
}
