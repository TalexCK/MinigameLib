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
import com.talexck.minigamelib.api.arena.ArenaPlayerStats;
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
import com.talexck.minigamelib.api.arena.ArenaVerticalBoundary;
import com.talexck.minigamelib.core.chest.ChestDefinition;
import com.talexck.minigamelib.core.chest.ChestLootEntry;
import com.talexck.minigamelib.core.chest.ChestPlacementMode;
import com.talexck.minigamelib.core.chest.ChestPosition;
import com.talexck.minigamelib.core.chest.DefaultChestService;
import com.talexck.minigamelib.core.resourcepack.ResourcePackService;
import com.talexck.minigamelib.core.world.DefaultWorldService;
import com.talexck.minigamelib.core.world.WorldCreateRequest;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ArenaController implements Listener {

  private static final long POST_GAME_RETURN_DELAY_TICKS = 20L * 15L;
  private static final int TAB_LAYOUT_SIZE = 80;
  private static final int TAB_COLUMN_WIDTH = 28;

  private final JavaPlugin plugin;
  private final DefaultWorldService worldService;
  private final DefaultChestService chestService;
  private final ResourcePackService resourcePackService;
  private final java.util.Set<String> infinitePlacedBlocks = ConcurrentHashMap.newKeySet();
  private final ConcurrentMap<String, ArenaTemplate> templates = new ConcurrentHashMap<>();
  private final ArenaRegistry registry = new ArenaRegistry();
  private final ConcurrentMap<String, RuntimeBoundary> boundaries = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, List<BlockSnapshot>> spawnCages = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, ActivePotionProjectile> potionProjectiles =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, DeathCredit> deathCredits = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, String> creeperOwners = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, RecentCreeperPlacement> recentCreeperPlacements =
      new ConcurrentHashMap<>();
  private final java.util.Set<String> warnedTabFeatures = ConcurrentHashMap.newKeySet();
  private final BukkitTask lobbyTabRefreshTask;

  public ArenaController(JavaPlugin plugin, DefaultWorldService worldService) {
    this.plugin = plugin;
    this.worldService = worldService;
    this.chestService = new DefaultChestService(plugin);
    this.resourcePackService = new ResourcePackService(plugin);
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
        clearArenaPlayerInventories(arena);
        clearSpawnCage(arena);
        chestService.stopArenaChests(arenaId, false);
        cancelBoundaryTasks(arena);
        boundaries.remove(arenaId);
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
        cancelBoundaryTasks(arena);
        boundaries.remove(arenaId);
        clearSpawnCage(arena);
        clearBossBar(arena);
        worldService.unloadWorld(arena.world().world(), arena.settings().saveWorldOnUnload());
        arena.setStatus(ArenaStatus.DESTROYED);
      }
    }
    HandlerList.unregisterAll(this);
    resourcePackService.shutdown();
  }

  private void setupWorld(RuntimeArena arena) {
    arena.world().world().setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
    ArenaBoundaryWall wall = arena.settings().initialBoundaryWall();
    if (wall != null) {
      boundaries.put(arena.arenaId(),
          new RuntimeBoundary(wall.centerX(), wall.centerZ(), (wall.x2() - wall.x1()) / 2.0,
              (wall.z2() - wall.z1()) / 2.0, arena.settings().verticalBoundary().lowerY(),
              arena.settings().verticalBoundary().upperY()));
      return;
    }
    ArenaPoint center = arena.layout().center();
    boundaries.put(arena.arenaId(),
        new RuntimeBoundary(center.x(), center.z(), arena.layout().initialBorderRadius(),
            arena.layout().initialBorderRadius(), arena.settings().verticalBoundary().lowerY(),
            arena.settings().verticalBoundary().upperY()));
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
        chest.timedDestruction(), chest.regenerationPeriodTicks(), chest.destructionDelayTicks(),
        chest.minItems(), chest.maxItems(), chest.displayName(), chest.splitStacks(),
        chest.blockMaterial(), chest.visualModelKey(), chest.openVisualModelKey());
  }

  private ChestLootEntry toChestLootEntry(ArenaLootEntry entry) {
    return new ChestLootEntry(entry.items(), entry.weight(), entry.earliestGenerationRound());
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
      createTeamSpawnCage(arena, world, spawnPoints);
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

  private void giveBeginningItems(RuntimeArena arena) {
    if (arena.settings().beginningItems().isEmpty()) {
      return;
    }
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null) {
        continue;
      }
      player.getInventory().clear();
      for (ArenaItemEntry entry : arena.settings().beginningItems()) {
        ItemStack stack = createArenaItemStack(arena, playerName, entry);
        if (entry.mode() == ArenaItemMode.INFINITE_OFFHAND) {
          player.getInventory().setItemInOffHand(stack);
          continue;
        }
        if (equipArmor(player, stack)) {
          continue;
        }
        player.getInventory().addItem(stack);
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
    } else if (entry.mode() == ArenaItemMode.INFINITE_OFFHAND) {
      Material material =
          arena.teamOf(playerName).map(this::concreteMaterial).orElse(Material.WHITE_CONCRETE);
      stack = new ItemStack(material, 64);
      applyItemName(stack, entry.name());
    } else if (entry.mode() == ArenaItemMode.TEAM_LEATHER_ARMOR) {
      Optional<ArenaTeamColor> teamColor = arena.teamOf(playerName);
      if (teamColor.isPresent()) {
        applyLeatherColor(stack, teamColor.get());
      }
    }
    return stack;
  }

  private boolean equipArmor(Player player, ItemStack stack) {
    return switch (stack.getType()) {
      case LEATHER_HELMET, CHAINMAIL_HELMET, IRON_HELMET, GOLDEN_HELMET, DIAMOND_HELMET, NETHERITE_HELMET, TURTLE_HELMET -> {
        player.getInventory().setHelmet(stack);
        yield true;
      }
      case LEATHER_CHESTPLATE, CHAINMAIL_CHESTPLATE, IRON_CHESTPLATE, GOLDEN_CHESTPLATE, DIAMOND_CHESTPLATE, NETHERITE_CHESTPLATE -> {
        player.getInventory().setChestplate(stack);
        yield true;
      }
      case LEATHER_LEGGINGS, CHAINMAIL_LEGGINGS, IRON_LEGGINGS, GOLDEN_LEGGINGS, DIAMOND_LEGGINGS, NETHERITE_LEGGINGS -> {
        player.getInventory().setLeggings(stack);
        yield true;
      }
      case LEATHER_BOOTS, CHAINMAIL_BOOTS, IRON_BOOTS, GOLDEN_BOOTS, DIAMOND_BOOTS, NETHERITE_BOOTS -> {
        player.getInventory().setBoots(stack);
        yield true;
      }
      default -> false;
    };
  }

  private void applyLeatherColor(ItemStack stack, ArenaTeamColor color) {
    ItemMeta meta = stack.getItemMeta();
    if (!(meta instanceof LeatherArmorMeta leatherMeta)) {
      return;
    }
    leatherMeta.setColor(leatherColor(color));
    stack.setItemMeta(leatherMeta);
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
      cancelBoundaryTasks(arena);
      boundaries.remove(arena.arenaId());
      clearSpawnCage(arena);
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
    clearSpawnCage(arena);
    setArenaPlayersGameMode(arena, GameMode.SURVIVAL);
    startInfiniteBlockMaintenance(arena);
    startBoundaryLifecycle(arena);
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
  public void onPlayerDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();
    RuntimeArena arena = findRunningArenaByPlayer(player.getName()).orElse(null);
    if (arena == null) {
      return;
    }
    event.setShowDeathMessages(false);
    event.deathMessage(null);

    Player directKiller = player.getKiller();
    String creditedKillerName = null;
    String messageKillerName = null;
    if (directKiller != null && arena.playerNames().contains(directKiller.getName())) {
      messageKillerName = directKiller.getName();
      if (isCreditableKill(arena, player.getName(), directKiller.getName())) {
        creditedKillerName = directKiller.getName();
        arena.recordKill(creditedKillerName);
        arena.listener().onKillPlayer(arena.handle(), creditedKillerName, player.getName());
      }
    }
    DeathCredit credit = validDeathCredit(player).orElse(null);
    if (messageKillerName == null && credit != null && credit.killerName() != null
        && arena.playerNames().contains(credit.killerName())) {
      messageKillerName = credit.killerName();
      if (isCreditableKill(arena, player.getName(), credit.killerName())) {
        creditedKillerName = credit.killerName();
        arena.recordKill(creditedKillerName);
        arena.listener().onKillPlayer(arena.handle(), creditedKillerName, player.getName());
      }
    }
    broadcastDeathMessage(arena, player.getName(), messageKillerName, credit);
    failPlayer(arena, player, creditedKillerName);
  }

  @EventHandler
  public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player victim)) {
      return;
    }
    RuntimeArena arena = findArenaByPlayer(victim.getName()).orElse(null);
    if (arena == null) {
      return;
    }
    if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
        || event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
        || event.getDamager() instanceof TNTPrimed || event.getDamager() instanceof Creeper) {
      recordDamageCredit(arena, victim, event.getDamager());
      return;
    }
    Player attacker = attackingPlayer(event.getDamager());
    if (attacker == null || attacker.equals(victim)) {
      return;
    }
    Optional<ArenaTeamColor> attackerTeam = arena.teamOf(attacker.getName());
    Optional<ArenaTeamColor> victimTeam = arena.teamOf(victim.getName());
    if (attackerTeam.isPresent() && attackerTeam.equals(victimTeam)) {
      event.setCancelled(true);
      deathCredits.remove(victim.getUniqueId());
      return;
    }
    recordDamageCredit(arena, victim, event.getDamager());
  }

  private boolean isCreditableKill(RuntimeArena arena, String victimName, String killerName) {
    if (killerName == null || victimName == null || victimName.equals(killerName)
        || !arena.playerNames().contains(killerName)) {
      return false;
    }
    Optional<ArenaTeamColor> victimTeam = arena.teamOf(victimName);
    Optional<ArenaTeamColor> killerTeam = arena.teamOf(killerName);
    return victimTeam.isEmpty() || killerTeam.isEmpty() || !victimTeam.equals(killerTeam);
  }

  @EventHandler
  public void onEntityPlace(EntityPlaceEvent event) {
    if (!(event.getEntity() instanceof Creeper) || event.getPlayer() == null) {
      return;
    }
    RuntimeArena arena = findArenaByPlayer(event.getPlayer().getName()).orElse(null);
    if (arena != null) {
      creeperOwners.put(event.getEntity().getUniqueId(), event.getPlayer().getName());
    }
  }

  @EventHandler
  public void onCreatureSpawn(CreatureSpawnEvent event) {
    if (!(event.getEntity() instanceof Creeper creeper)
        || event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
      return;
    }
    findRecentCreeperOwner(event.getLocation()).ifPresent(owner -> {
      if (findArenaByPlayer(owner).isPresent()) {
        creeperOwners.put(creeper.getUniqueId(), owner);
      }
    });
  }

  @EventHandler
  public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
    event.message(null);
  }

  private void recordDamageCredit(RuntimeArena arena, Player victim, Entity damager) {
    if (damager instanceof TNTPrimed tnt) {
      String killerName = tnt.getSource() instanceof Player player ? player.getName() : null;
      deathCredits.put(victim.getUniqueId(), new DeathCredit(killerName, DeathSource.TNT, "TNT",
          System.currentTimeMillis() + 10_000L));
      return;
    }
    if (damager instanceof Creeper creeper) {
      String killerName = creeperOwners.get(creeper.getUniqueId());
      deathCredits.put(victim.getUniqueId(), new DeathCredit(killerName, DeathSource.CREEPER, "苦力怕",
          System.currentTimeMillis() + 10_000L));
      return;
    }
    Player attacker = attackingPlayer(damager);
    if (attacker != null && arena.playerNames().contains(attacker.getName())) {
      deathCredits.put(victim.getUniqueId(), new DeathCredit(attacker.getName(), DeathSource.PLAYER,
          "", System.currentTimeMillis() + 10_000L));
    }
  }

  private Optional<DeathCredit> validDeathCredit(Player player) {
    DeathCredit credit = deathCredits.remove(player.getUniqueId());
    if (credit == null || credit.expiresAtMillis() < System.currentTimeMillis()) {
      return Optional.empty();
    }
    return Optional.of(credit);
  }

  private void broadcastDeathMessage(RuntimeArena arena, String victimName, String killerName,
      DeathCredit credit) {
    String template = deathTemplate(arena, killerName, credit);
    for (String playerName : arena.playerNames()) {
      Player viewer = Bukkit.getPlayerExact(playerName);
      if (viewer != null) {
        viewer.sendMessage(renderDeathComponent(arena, template, victimName, killerName, credit));
      }
    }
  }

  private String deathTemplate(RuntimeArena arena, String killerName, DeathCredit credit) {
    if (credit == null) {
      return killerName == null ? arena.settings().messages().deathGeneric()
          : arena.settings().messages().deathByPlayer();
    }
    return switch (credit.source()) {
      case TNT -> arena.settings().messages().deathByTnt();
      case CREEPER -> arena.settings().messages().deathByCreeper();
      case POTION -> arena.settings().messages().deathByPotion();
      case PLAYER -> arena.settings().messages().deathByPlayer();
    };
  }

  private Component renderDeathComponent(RuntimeArena arena, String template, String victimName,
      String killerName, DeathCredit credit) {
    Component result = Component.empty();
    int index = 0;
    while (index < template.length()) {
      if (template.startsWith("{victim}", index)) {
        result = result.append(coloredPlayerName(arena, victimName));
        index += "{victim}".length();
      } else if (template.startsWith("{killer}", index)) {
        String renderedKiller =
            killerName == null && credit != null ? credit.killerName() : killerName;
        result = result.append(renderedKiller == null ? Component.text("未知来源")
            : coloredPlayerName(arena, renderedKiller));
        index += "{killer}".length();
      } else if (template.startsWith("{source}", index)) {
        result = result
            .append(Component.text(credit == null ? "" : credit.sourceName(), NamedTextColor.GOLD));
        index += "{source}".length();
      } else {
        int next = nextDeathPlaceholderIndex(template, index);
        result = result.append(coloredComponent(template.substring(index, next)));
        index = next;
      }
    }
    return result;
  }

  private int nextDeathPlaceholderIndex(String template, int start) {
    int next = template.length();
    for (String placeholder : List.of("{victim}", "{killer}", "{source}")) {
      int index = template.indexOf(placeholder, start);
      if (index >= 0) {
        next = Math.min(next, index);
      }
    }
    return next;
  }

  private Component coloredPlayerName(RuntimeArena arena, String playerName) {
    NamedTextColor color =
        arena.teamOf(playerName).map(this::tabColor).orElse(NamedTextColor.WHITE);
    return Component.text(playerName, color);
  }

  private void failPlayer(RuntimeArena arena, Player player, String killerName) {
    ArenaTeamColor teamColor = arena.teamOf(player.getName()).orElse(null);
    boolean wasFailed = arena.isFailed(player.getName());
    boolean teamWasFailed = teamColor != null && arena.isTeamFailed(teamColor);
    arena.recordDeath(player.getName());
    if (killerName != null) {
      arena.listener().onPlayerKilled(arena.handle(), player.getName(), killerName);
    }
    if (!wasFailed && arena.isFailed(player.getName())) {
      arena.listener().onPlayerFailed(arena.handle(), player.getName(), teamColor);
    }
    if (teamColor != null && !teamWasFailed && arena.isTeamFailed(teamColor)) {
      List<String> failedTeamPlayers =
          arena.teams().stream().filter(team -> team.color() == teamColor).findFirst()
              .map(ArenaTeam::playerNames).orElse(List.of());
      arena.listener().onTeamFailed(arena.handle(), teamColor, failedTeamPlayers);
    }
    applyScoreboards(arena, 0);
    checkVictory(arena);
  }

  @EventHandler
  public void onPlayerMove(PlayerMoveEvent event) {
    if (event.getFrom().getY() == event.getTo().getY()) {
      return;
    }
    Player player = event.getPlayer();
    RuntimeArena arena = findRunningArenaByPlayer(player.getName()).orElse(null);
    if (arena == null) {
      return;
    }
    double y = event.getTo().getY();
    RuntimeBoundary runtimeBoundary = boundaries.get(arena.arenaId());
    boolean outsideConfigured = runtimeBoundary != null && runtimeBoundary.outsideY(y);
    boolean belowWorld = y < player.getWorld().getMinHeight();
    if (!outsideConfigured && !belowWorld) {
      return;
    }
    if (!arena.isFailed(player.getName())) {
      player.setHealth(0.0);
    }
  }

  @EventHandler
  public void onEntityExplode(EntityExplodeEvent event) {
    if (protectArenaExplosion(event.getLocation().getWorld(), event.blockList())) {
      event.setYield(0.0f);
    }
  }

  @EventHandler
  public void onBlockExplode(BlockExplodeEvent event) {
    if (protectArenaExplosion(event.getBlock().getWorld(), event.blockList())) {
      event.setYield(0.0f);
    }
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

  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    RuntimeArena arena = findArenaByPlayer(player.getName()).orElse(null);
    if (arena == null) {
      return;
    }
    ItemStack hand = event.getItemInHand();
    if (hand.getType() == Material.TNT && findIgniteTntItem(arena, hand).isPresent()) {
      ignitePlacedTnt(event.getBlockPlaced(), player);
    }
    Optional<ArenaItemEntry> infiniteEntry = findInfiniteBlockItem(arena, hand);
    if (infiniteEntry.isPresent()) {
      if (infiniteEntry.get().mode() == ArenaItemMode.INFINITE_OFFHAND
          && event.getHand() == EquipmentSlot.HAND) {
        event.setCancelled(true);
        clearMainHandInfiniteOffhandBlock(arena, player, infiniteEntry.get());
        ensureOffhandInfiniteBlock(arena, player, infiniteEntry.get());
        return;
      }
      infinitePlacedBlocks.add(blockKey(event.getBlockPlaced()));
      Bukkit.getScheduler().runTask(plugin,
          () -> refillInfiniteItem(arena, player, infiniteEntry.get(), event.getHand()));
    }
  }

  @EventHandler
  public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
    Player player = event.getPlayer();
    RuntimeArena arena = findArenaByPlayer(player.getName()).orElse(null);
    if (arena == null) {
      return;
    }
    ArenaItemEntry offhandEntry = arena.settings().beginningItems().stream()
        .filter(entry -> entry.mode() == ArenaItemMode.INFINITE_OFFHAND).findFirst().orElse(null);
    if (offhandEntry == null) {
      return;
    }
    boolean movingInfiniteBlock =
        findItemEntry(arena, event.getOffHandItem(), ArenaItemMode.INFINITE_OFFHAND).isPresent()
            || findItemEntry(arena, event.getMainHandItem(), ArenaItemMode.INFINITE_OFFHAND)
                .isPresent();
    if (!movingInfiniteBlock) {
      return;
    }
    event.setCancelled(true);
    clearMainHandInfiniteOffhandBlock(arena, player, offhandEntry);
    ensureOffhandInfiniteBlock(arena, player, offhandEntry);
  }

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    RuntimeArena arena = findArenaByPlayer(event.getPlayer().getName()).orElse(null);
    if (arena != null) {
      event.setDropItems(false);
      event.setExpToDrop(0);
    }
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
    if (item != null && item.getType() == Material.CREEPER_SPAWN_EGG) {
      rememberCreeperPlacement(player, event);
    }
    ArenaItemEntry selfPotion = findItemEntry(arena, item, ArenaItemMode.SELF_POTION).orElse(null);
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

  private void rememberCreeperPlacement(Player player, PlayerInteractEvent event) {
    Location location = event.getClickedBlock() == null ? player.getLocation()
        : event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
    recentCreeperPlacements.put(player.getUniqueId(),
        new RecentCreeperPlacement(player.getName(), location, System.currentTimeMillis()));
  }

  private Optional<String> findRecentCreeperOwner(Location spawnLocation) {
    long now = System.currentTimeMillis();
    recentCreeperPlacements.entrySet().removeIf(entry -> now - entry.getValue().createdAtMillis()
        > 3_000L);
    return recentCreeperPlacements.values().stream()
        .filter(placement -> placement.location().getWorld() != null
            && placement.location().getWorld().equals(spawnLocation.getWorld()))
        .filter(placement -> placement.location().distanceSquared(spawnLocation) <= 16.0)
        .min(java.util.Comparator.comparingDouble(
            placement -> placement.location().distanceSquared(spawnLocation)))
        .map(RecentCreeperPlacement::playerName);
  }

  private void checkVictory(RuntimeArena arena) {
    if (arena.settings().victoryCondition() == null) {
      return;
    }
    if (arena.aliveTeamCount() == 0) {
      stopArena(arena.arenaId(), ArenaStopReason.NORMAL);
      return;
    }
    arena.singleAliveTeam().ifPresent(winner -> {
      arena.setWinningTeam(winner);
      stopArena(arena.arenaId(), ArenaStopReason.NORMAL);
    });
  }

  private Optional<RuntimeArena> findRunningArenaByPlayer(String playerName) {
    return registry.findRunningByPlayer(playerName);
  }

  private Optional<RuntimeArena> findArenaByPlayer(String playerName) {
    return registry.findByPlayer(playerName);
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
        .flatMap(entry -> entry.items().stream()).forEach(items::add);
    return items;
  }

  private Optional<ArenaItemEntry> findIgniteTntItem(RuntimeArena arena, ItemStack stack) {
    if (stack == null || stack.getType() != Material.TNT) {
      return Optional.empty();
    }
    return allConfiguredItems(arena).stream().filter(ArenaItemEntry::igniteTntOnPlace)
        .filter(entry -> entry.item().getType() == Material.TNT)
        .filter(entry -> matchesArenaItem(arena, stack, entry)).findFirst();
  }

  private void ignitePlacedTnt(Block block, Player source) {
    Location location = block.getLocation().add(0.5, 0.0, 0.5);
    block.setType(Material.AIR);
    block.getWorld().spawn(location, TNTPrimed.class, tnt -> tnt.setSource(source));
  }

  private boolean matchesArenaItem(RuntimeArena arena, ItemStack stack, ArenaItemEntry entry) {
    if (entry.mode() == ArenaItemMode.INFINITE || entry.mode() == ArenaItemMode.INFINITE_OFFHAND) {
      return isConcrete(stack.getType());
    }
    if (stack.getType() != entry.item().getType()) {
      return false;
    }
    if (entry.mode() == ArenaItemMode.POTION || entry.mode() == ArenaItemMode.SELF_POTION
        || entry.mode() == ArenaItemMode.TEAM_LEATHER_ARMOR) {
      return itemDisplayName(stack).equals(entry.name());
    }
    return true;
  }

  private String itemDisplayName(ItemStack stack) {
    ItemMeta meta = stack.getItemMeta();
    if (meta == null || !meta.hasDisplayName() || meta.displayName() == null) {
      return "";
    }
    return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
  }

  private void refillInfiniteItem(RuntimeArena arena, Player player) {
    ArenaItemEntry entry = arena.settings().beginningItems().stream()
        .filter(item -> item.mode() == ArenaItemMode.INFINITE
            || item.mode() == ArenaItemMode.INFINITE_OFFHAND)
        .findFirst().orElse(null);
    if (entry == null) {
      return;
    }
    Material material =
        arena.teamOf(player.getName()).map(this::concreteMaterial).orElse(Material.WHITE_CONCRETE);
    if (entry.mode() == ArenaItemMode.INFINITE_OFFHAND) {
      player.getInventory().setItemInOffHand(createArenaItemStack(arena, player.getName(), entry));
      return;
    }
    for (ItemStack stack : player.getInventory().getContents()) {
      if (stack != null && stack.getType() == material) {
        stack.setAmount(64);
        return;
      }
    }
    player.getInventory().addItem(createArenaItemStack(arena, player.getName(), entry));
  }

  private Optional<ArenaItemEntry> findInfiniteBlockItem(RuntimeArena arena, ItemStack stack) {
    Optional<ArenaItemEntry> mainHandEntry = findItemEntry(arena, stack, ArenaItemMode.INFINITE);
    if (mainHandEntry.isPresent()) {
      return mainHandEntry;
    }
    return findItemEntry(arena, stack, ArenaItemMode.INFINITE_OFFHAND);
  }

  private void refillInfiniteItem(RuntimeArena arena, Player player, ArenaItemEntry entry,
      EquipmentSlot hand) {
    ItemStack refill = createArenaItemStack(arena, player.getName(), entry);
    if (entry.mode() == ArenaItemMode.INFINITE_OFFHAND || hand == EquipmentSlot.OFF_HAND) {
      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        clearMainHandInfiniteOffhandBlock(arena, player, entry);
        player.getInventory().setItemInOffHand(refill);
      }, 1L);
      return;
    }
    if (hand == EquipmentSlot.HAND) {
      Bukkit.getScheduler().runTaskLater(plugin,
          () -> player.getInventory().setItemInMainHand(refill), 1L);
      return;
    }
    refillInfiniteItem(arena, player);
  }

  private void ensureOffhandInfiniteBlock(RuntimeArena arena, Player player, ArenaItemEntry entry) {
    Bukkit.getScheduler().runTask(plugin, () -> player.getInventory()
        .setItemInOffHand(createArenaItemStack(arena, player.getName(), entry)));
  }

  private void clearMainHandInfiniteOffhandBlock(RuntimeArena arena, Player player,
      ArenaItemEntry entry) {
    if (entry.mode() != ArenaItemMode.INFINITE_OFFHAND) {
      return;
    }
    ItemStack mainHand = player.getInventory().getItemInMainHand();
    if (findItemEntry(arena, mainHand, ArenaItemMode.INFINITE_OFFHAND).isPresent()) {
      player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
    }
  }

  private void launchPotionFireball(RuntimeArena arena, Player player, ArenaItemEntry entry) {
    ArenaPotionItemConfig config = entry.potionConfig();
    ItemStack displayStack = projectileDisplayStack(entry);
    Snowball snowball = player.launchProjectile(Snowball.class);
    snowball.setItem(displayStack);
    snowball.setVelocity(player.getLocation().getDirection().normalize().multiply(1.25));
    snowball.setGravity(entry.item().getType() == Material.SNOWBALL);
    snowball.setPersistent(false);
    potionProjectiles.put(snowball.getUniqueId(),
        new ActivePotionProjectile(arena.arenaId(), config, player.getName(), entry.name()));
    new BukkitRunnable() {
      private int ticks;

      @Override
      public void run() {
        RuntimeArena currentArena = registry.get(arena.arenaId());
        if (currentArena == null || snowball.isDead() || !snowball.isValid()) {
          potionProjectiles.remove(snowball.getUniqueId());
          cancel();
          return;
        }
        if (ticks >= 80) {
          explodePotionProjectile(snowball);
          cancel();
          return;
        }
        spawnPotionParticle(snowball.getLocation(), config, 2, 0.08, 0.08, 0.08);
        ticks++;
      }
    }.runTaskTimer(plugin, 0L, 1L);
  }

  @SuppressWarnings("deprecation")
  private ItemStack projectileDisplayStack(ArenaItemEntry entry) {
    ItemStack stack = entry.createStack();
    stack.setAmount(1);
    ArenaPotionItemConfig config = entry.potionConfig();
    if (config.projectileCustomModelData() > 0 || !config.itemModelKey().isBlank()) {
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
        if (config.projectileCustomModelData() > 0) {
          meta.setCustomModelData(config.projectileCustomModelData());
        }
        applyItemModel(meta, config);
        stack.setItemMeta(meta);
      }
    }
    return stack;
  }

  private void applyItemModel(ItemMeta meta, ArenaPotionItemConfig config) {
    if (config == null || config.itemModelKey().isBlank()) {
      return;
    }
    NamespacedKey key = NamespacedKey.fromString(config.itemModelKey());
    if (key != null) {
      meta.setItemModel(key);
    }
  }

  @EventHandler
  public void onProjectileHit(ProjectileHitEvent event) {
    if (event.getEntity() instanceof Snowball snowball
        && potionProjectiles.containsKey(snowball.getUniqueId())) {
      explodePotionProjectile(snowball);
    }
  }

  private void explodePotionProjectile(Snowball snowball) {
    ActivePotionProjectile projectile = potionProjectiles.remove(snowball.getUniqueId());
    if (projectile == null) {
      return;
    }
    Location location = snowball.getLocation();
    snowball.remove();
    startPotionSphere(projectile.arenaId(), location, projectile.config(), projectile.shooterName(),
        projectile.itemName());
  }

  private void applySelfPotion(Player player, ArenaItemEntry entry) {
    ArenaPotionItemConfig config = entry.potionConfig();
    player.addPotionEffect(new PotionEffect(config.effectType(),
        Math.max(1, (int) toTicks(config.effectDuration())), config.amplifier(), true, true, true));
  }

  private void startPotionSphere(String arenaId, Location center, ArenaPotionItemConfig config,
      String shooterName, String itemName) {
    long durationTicks = toTicks(config.duration());
    long effectTicks = Math.max(1L, toTicks(config.effectDuration()));
    new BukkitRunnable() {
      private long elapsedTicks;

      @Override
      public void run() {
        RuntimeArena arena = registry.get(arenaId);
        if (arena == null || elapsedTicks > durationTicks) {
          cancel();
          return;
        }
        spawnPotionSphereParticles(center, config);
        double radiusSquared = config.radius() * config.radius();
        for (String playerName : arena.playerNames()) {
          Player player = Bukkit.getPlayerExact(playerName);
          if (player != null && player.getWorld().equals(center.getWorld())
              && player.getLocation().distanceSquared(center) <= radiusSquared) {
            if (isOffensiveEffect(config.effectType())) {
              deathCredits.put(player.getUniqueId(),
                  new DeathCredit(shooterName, DeathSource.POTION,
                      itemName == null || itemName.isBlank() ? "药水球" : itemName,
                      System.currentTimeMillis() + 12_000L));
            }
            player.addPotionEffect(new PotionEffect(config.effectType(), (int) effectTicks,
                config.amplifier(), true, true, true));
          }
        }
        elapsedTicks += 20L;
      }
    }.runTaskTimer(plugin, 0L, 20L);
  }

  private void spawnPotionSphereParticles(Location center, ArenaPotionItemConfig config) {
    double radius = config.radius();
    for (int index = 0; index < 42; index++) {
      double theta = 2.399963229728653 * index;
      double y = 1.0 - (2.0 * index / 41.0);
      double circleRadius = Math.sqrt(Math.max(0.0, 1.0 - y * y));
      Location point = center.clone().add(Math.cos(theta) * circleRadius * radius,
          y * radius * 0.75, Math.sin(theta) * circleRadius * radius);
      spawnPotionParticle(point, config, 1, 0.02, 0.02, 0.02);
    }
  }

  private void spawnPotionParticle(Location location, ArenaPotionItemConfig config, int count,
      double offsetX, double offsetY, double offsetZ) {
    Particle particle = particleFor(config.effectType());
    if (particle == Particle.DUST) {
      location.getWorld().spawnParticle(Particle.DUST, location, count, offsetX, offsetY, offsetZ,
          0.0, dustFor(config.effectType()));
      return;
    }
    location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, 0.01);
  }

  private Particle particleFor(org.bukkit.potion.PotionEffectType effectType) {
    if (effectType == org.bukkit.potion.PotionEffectType.INSTANT_DAMAGE) {
      return Particle.DAMAGE_INDICATOR;
    }
    if (effectType == org.bukkit.potion.PotionEffectType.POISON) {
      return Particle.DUST;
    }
    if (effectType == org.bukkit.potion.PotionEffectType.REGENERATION) {
      return Particle.HEART;
    }
    if (effectType == org.bukkit.potion.PotionEffectType.LEVITATION) {
      return Particle.CLOUD;
    }
    return Particle.DUST;
  }

  private Particle.DustOptions dustFor(org.bukkit.potion.PotionEffectType effectType) {
    if (effectType == org.bukkit.potion.PotionEffectType.POISON) {
      return new Particle.DustOptions(Color.fromRGB(0x4E9331), 1.15f);
    }
    return new Particle.DustOptions(Color.WHITE, 1.0f);
  }

  private boolean isOffensiveEffect(org.bukkit.potion.PotionEffectType effectType) {
    return effectType == org.bukkit.potion.PotionEffectType.INSTANT_DAMAGE
        || effectType == org.bukkit.potion.PotionEffectType.POISON;
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

  private Color leatherColor(ArenaTeamColor color) {
    return switch (color) {
      case RED -> Color.fromRGB(0xB02E26);
      case YELLOW -> Color.fromRGB(0xF1C232);
      case GREEN -> Color.fromRGB(0x5E7C16);
      case BLUE -> Color.fromRGB(0x3C44AA);
      case ORANGE -> Color.fromRGB(0xF9801D);
      case PURPLE -> Color.fromRGB(0x8932B8);
      case WHITE -> Color.fromRGB(0xF9FFFE);
      case PINK -> Color.fromRGB(0xF38BAA);
      case GRAY -> Color.fromRGB(0x474F52);
      case CYAN -> Color.fromRGB(0x169C9C);
    };
  }

  private void setArenaPlayersGameMode(RuntimeArena arena, GameMode gameMode) {
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        player.setGameMode(gameMode);
      }
    }
  }

  private void clearArenaPlayerInventories(RuntimeArena arena) {
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null) {
        continue;
      }
      player.getInventory().clear();
      player.getInventory().setArmorContents(null);
      player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
    }
  }

  private void startInfiniteBlockMaintenance(RuntimeArena arena) {
    BukkitTask task = new BukkitRunnable() {
      @Override
      public void run() {
        RuntimeArena current = registry.get(arena.arenaId());
        if (current == null || current.status() != ArenaStatus.RUNNING) {
          cancel();
          return;
        }
        for (String playerName : current.playerNames()) {
          Player player = Bukkit.getPlayerExact(playerName);
          if (player == null) {
            continue;
          }
          current.settings().beginningItems().stream()
              .filter(entry -> entry.mode() == ArenaItemMode.INFINITE_OFFHAND).findFirst()
              .ifPresent(entry -> ensureInfiniteOffhand(current, player, entry));
        }
      }
    }.runTaskTimer(plugin, 1L, 5L);
    arena.boundaryTasks().add(task);
  }

  private void ensureInfiniteOffhand(RuntimeArena arena, Player player, ArenaItemEntry entry) {
    ItemStack offhand = player.getInventory().getItemInOffHand();
    Material material =
        arena.teamOf(player.getName()).map(this::concreteMaterial).orElse(Material.WHITE_CONCRETE);
    if (offhand == null || offhand.getType() != material || offhand.getAmount() < 64) {
      player.getInventory().setItemInOffHand(createArenaItemStack(arena, player.getName(), entry));
    }
  }

  private void applyPlayerListName(RuntimeArena arena, Player player) {
    NamedTextColor color =
        arena.teamOf(player.getName()).map(this::tabColor).orElse(NamedTextColor.WHITE);
    player.playerListName(Component.text(player.getName(), color));
  }

  private NamedTextColor tabColor(ArenaTeamColor color) {
    return switch (color) {
      case RED -> NamedTextColor.RED;
      case YELLOW -> NamedTextColor.YELLOW;
      case GREEN -> NamedTextColor.GREEN;
      case BLUE -> NamedTextColor.BLUE;
      case ORANGE -> NamedTextColor.GOLD;
      case PURPLE -> NamedTextColor.LIGHT_PURPLE;
      case WHITE -> NamedTextColor.WHITE;
      case PINK -> NamedTextColor.LIGHT_PURPLE;
      case GRAY -> NamedTextColor.GRAY;
      case CYAN -> NamedTextColor.AQUA;
    };
  }

  private String tabColorCode(ArenaTeamColor color) {
    return switch (color) {
      case RED -> "&c";
      case YELLOW -> "&e";
      case GREEN -> "&a";
      case BLUE -> "&9";
      case ORANGE -> "&6";
      case PURPLE -> "&d";
      case WHITE -> "&f";
      case PINK -> "&d";
      case GRAY -> "&7";
      case CYAN -> "&b";
    };
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
    return switch (color) {
      case PINK -> BarColor.PINK;
      case BLUE -> BarColor.BLUE;
      case RED -> BarColor.RED;
      case GREEN -> BarColor.GREEN;
      case YELLOW -> BarColor.YELLOW;
      case PURPLE -> BarColor.PURPLE;
      case WHITE -> BarColor.WHITE;
    };
  }

  private BarStyle tabBarStyle(org.bukkit.boss.BarStyle style) {
    return switch (style) {
      case SOLID -> BarStyle.PROGRESS;
      case SEGMENTED_6 -> BarStyle.NOTCHED_6;
      case SEGMENTED_10 -> BarStyle.NOTCHED_10;
      case SEGMENTED_12 -> BarStyle.NOTCHED_12;
      case SEGMENTED_20 -> BarStyle.NOTCHED_20;
    };
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

  private void scheduleBoundaryStages(RuntimeArena arena) {
    long delayTicks = 0L;
    for (ArenaBoundaryStage stage : arena.settings().boundaryStages()) {
      delayTicks += toTicks(stage.delayAfterPreviousStage());
      long durationTicks = toTicks(stage.duration());
      BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
          () -> startBoundaryShrink(arena, stage, durationTicks), delayTicks);
      arena.boundaryTasks().add(task);
      delayTicks += durationTicks;
    }
  }

  private void cancelBoundaryTasks(RuntimeArena arena) {
    arena.boundaryTasks().forEach(BukkitTask::cancel);
    arena.boundaryTasks().clear();
  }

  private void createTeamSpawnCage(RuntimeArena arena, World world, List<ArenaPoint> spawnPoints) {
    if (spawnPoints.isEmpty()) {
      return;
    }
    SpawnGeometry.TeamSpawnBounds bounds = SpawnGeometry.teamSpawnBounds(spawnPoints).orElse(null);
    if (bounds == null) {
      for (ArenaPoint point : spawnPoints) {
        createSpawnCage(arena, world, blockCoordinate(point.x()), blockCoordinate(point.y()),
            blockCoordinate(point.z()));
      }
      return;
    }
    createSpawnCage(arena, world, bounds.centerX(), bounds.baseY(), bounds.centerZ());
  }

  private void createSpawnCage(RuntimeArena arena, World world, int centerX, int baseY,
      int centerZ) {
    List<BlockSnapshot> snapshots =
        spawnCages.computeIfAbsent(arena.arenaId(), ignored -> new ArrayList<>());
    for (int yOffset = 0; yOffset < 3; yOffset++) {
      for (int dx = -2; dx <= 2; dx++) {
        for (int dz = -2; dz <= 2; dz++) {
          if (!isSpawnCageBlock(dx, dz)) {
            continue;
          }
          Block block = world.getBlockAt(centerX + dx, baseY + yOffset, centerZ + dz);
          if (!block.getType().isAir()) {
            continue;
          }
          snapshots.add(new BlockSnapshot(block, block.getType()));
          block.setType(Material.BARRIER, false);
        }
      }
    }
  }

  private boolean isSpawnCageBlock(int dx, int dz) {
    boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
    boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
    return edge && !corner;
  }

  private int blockCoordinate(double value) {
    return SpawnGeometry.blockCoordinate(value);
  }

  private void clearSpawnCage(RuntimeArena arena) {
    List<BlockSnapshot> snapshots = spawnCages.remove(arena.arenaId());
    if (snapshots == null) {
      return;
    }
    for (BlockSnapshot snapshot : snapshots) {
      if (snapshot.block().getType() == Material.BARRIER) {
        snapshot.block().setType(snapshot.material(), false);
      }
    }
  }

  private void startBoundaryLifecycle(RuntimeArena arena) {
    BukkitTask task = new BukkitRunnable() {
      private int ticks;

      @Override
      public void run() {
        RuntimeArena current = registry.get(arena.arenaId());
        if (current == null || current.status() != ArenaStatus.RUNNING) {
          cancel();
          return;
        }
        RuntimeBoundary boundary = boundaries.get(arena.arenaId());
        if (boundary == null) {
          return;
        }
        spawnBoundaryParticles(current, boundary);
        if (ticks % 2 == 0) {
          damagePlayersOutsideBoundary(current, boundary);
        }
        ticks++;
      }
    }.runTaskTimer(plugin, 0L, 10L);
    arena.boundaryTasks().add(task);
  }

  private void startBoundaryShrink(RuntimeArena arena, ArenaBoundaryStage stage,
      long durationTicks) {
    RuntimeBoundary boundary = boundaries.get(arena.arenaId());
    if (boundary == null) {
      return;
    }
    double startX = boundary.currentXDistance();
    double startZ = boundary.currentZDistance();
    double startLowerY = boundary.currentLowerY();
    double startUpperY = boundary.currentUpperY();
    double targetX = Math.max(1.0, stage.xDistanceFromCenter());
    double targetZ = Math.max(1.0, stage.zDistanceFromCenter());
    double targetLowerY =
        stage.lowerY() == ArenaVerticalBoundary.DISABLED ? startLowerY : stage.lowerY();
    double targetUpperY =
        stage.upperY() == ArenaVerticalBoundary.DISABLED ? startUpperY : stage.upperY();
    boundary.setTarget(targetX, targetZ, targetLowerY, targetUpperY);
    if (durationTicks <= 0L) {
      boundary.setCurrent(targetX, targetZ, targetLowerY, targetUpperY);
      boundary.clearTarget();
      return;
    }
    BukkitTask task = new BukkitRunnable() {
      private long elapsedTicks;

      @Override
      public void run() {
        RuntimeArena current = registry.get(arena.arenaId());
        if (current == null || current.status() != ArenaStatus.RUNNING) {
          cancel();
          return;
        }
        double progress = Math.min(1.0, (double) elapsedTicks / durationTicks);
        boundary.setCurrent(lerp(startX, targetX, progress), lerp(startZ, targetZ, progress),
            lerpBoundaryY(startLowerY, targetLowerY, progress),
            lerpBoundaryY(startUpperY, targetUpperY, progress));
        if (progress >= 1.0) {
          boundary.clearTarget();
          cancel();
          return;
        }
        elapsedTicks += 5L;
      }
    }.runTaskTimer(plugin, 0L, 5L);
    arena.boundaryTasks().add(task);
  }

  private void spawnBoundaryParticles(RuntimeArena arena, RuntimeBoundary boundary) {
    World world = arena.world().world();
    Particle.DustOptions red = new Particle.DustOptions(Color.RED, 1.25f);
    Particle.DustOptions orange = new Particle.DustOptions(Color.fromRGB(255, 140, 0), 1.25f);
    spawnVerticalBoundaryParticles(world, boundary, red, orange);
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null || !player.getWorld().equals(world)) {
        continue;
      }
      double y = player.getLocation().getY() + 0.15;
      spawnBoundaryRectangle(world, boundary.centerX(), boundary.centerZ(),
          boundary.currentXDistance(), boundary.currentZDistance(), y, red);
      if (boundary.hasTarget()) {
        spawnBoundaryRectangle(world, boundary.centerX(), boundary.centerZ(),
            boundary.targetXDistance(), boundary.targetZDistance(), y + 0.35, orange);
      }
    }
  }

  private void spawnVerticalBoundaryParticles(World world, RuntimeBoundary boundary,
      Particle.DustOptions current, Particle.DustOptions target) {
    spawnBoundaryYRectangle(world, boundary, boundary.currentLowerY(), boundary.currentXDistance(),
        boundary.currentZDistance(), current);
    spawnBoundaryYRectangle(world, boundary, boundary.currentUpperY(), boundary.currentXDistance(),
        boundary.currentZDistance(), current);
    if (!boundary.hasTarget()) {
      return;
    }
    spawnBoundaryYRectangle(world, boundary, boundary.targetLowerY(), boundary.targetXDistance(),
        boundary.targetZDistance(), target);
    spawnBoundaryYRectangle(world, boundary, boundary.targetUpperY(), boundary.targetXDistance(),
        boundary.targetZDistance(), target);
  }

  private void spawnBoundaryYRectangle(World world, RuntimeBoundary boundary, double y,
      double xDistance, double zDistance, Particle.DustOptions dust) {
    if (y == ArenaVerticalBoundary.DISABLED) {
      return;
    }
    spawnBoundaryRectangle(world, boundary.centerX(), boundary.centerZ(), xDistance, zDistance, y,
        dust);
  }

  private void spawnBoundaryRectangle(World world, double centerX, double centerZ, double xDistance,
      double zDistance, double y, Particle.DustOptions dust) {
    double step = 3.0;
    double minX = centerX - xDistance;
    double maxX = centerX + xDistance;
    double minZ = centerZ - zDistance;
    double maxZ = centerZ + zDistance;
    for (double x = minX; x <= maxX; x += step) {
      spawnDust(world, x, y, minZ, dust);
      spawnDust(world, x, y, maxZ, dust);
    }
    for (double z = minZ; z <= maxZ; z += step) {
      spawnDust(world, minX, y, z, dust);
      spawnDust(world, maxX, y, z, dust);
    }
  }

  private void spawnDust(World world, double x, double y, double z, Particle.DustOptions dust) {
    world.spawnParticle(Particle.DUST, new Location(world, x, y, z), 1, dust);
  }

  private void damagePlayersOutsideBoundary(RuntimeArena arena, RuntimeBoundary boundary) {
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null || arena.isFailed(playerName)
          || !player.getWorld().equals(arena.world().world())) {
        continue;
      }
      Location location = player.getLocation();
      if (Math.abs(location.getX() - boundary.centerX()) > boundary.currentXDistance()
          || Math.abs(location.getZ() - boundary.centerZ()) > boundary.currentZDistance()
          || boundary.outsideY(location.getY())) {
        player.damage(2.0);
      }
    }
  }

  private boolean protectArenaExplosion(World world, List<Block> blocks) {
    if (!registry.hasActiveArenaInWorld(world)) {
      return false;
    }
    blocks.removeIf(block -> block.getType() == Material.CHEST
        || block.getType() == Material.TRAPPED_CHEST || chestService.isActiveChest(block));
    return true;
  }

  private double lerp(double start, double end, double progress) {
    return BoundaryMath.lerp(start, end, progress);
  }

  private double lerpBoundaryY(double start, double end, double progress) {
    return BoundaryMath.lerpBoundaryY(start, end, progress);
  }

  private Player attackingPlayer(Entity damager) {
    if (damager instanceof Player player) {
      return player;
    }
    if (damager instanceof Projectile projectile
        && projectile.getShooter() instanceof Player player) {
      return player;
    }
    return null;
  }

  private long toTicks(java.time.Duration duration) {
    return Math.max(0L, duration.toMillis() / 50L);
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

  private static final class RuntimeBoundary {
    private final double centerX;
    private final double centerZ;
    private double currentXDistance;
    private double currentZDistance;
    private double currentLowerY;
    private double currentUpperY;
    private double targetXDistance = -1.0;
    private double targetZDistance = -1.0;
    private double targetLowerY = ArenaVerticalBoundary.DISABLED;
    private double targetUpperY = ArenaVerticalBoundary.DISABLED;

    private RuntimeBoundary(double centerX, double centerZ, double currentXDistance,
        double currentZDistance, double currentLowerY, double currentUpperY) {
      this.centerX = centerX;
      this.centerZ = centerZ;
      this.currentXDistance = Math.max(1.0, currentXDistance);
      this.currentZDistance = Math.max(1.0, currentZDistance);
      this.currentLowerY = currentLowerY;
      this.currentUpperY = currentUpperY;
    }

    private double centerX() {
      return centerX;
    }

    private double centerZ() {
      return centerZ;
    }

    private double currentXDistance() {
      return currentXDistance;
    }

    private double currentZDistance() {
      return currentZDistance;
    }

    private double targetXDistance() {
      return targetXDistance;
    }

    private double targetZDistance() {
      return targetZDistance;
    }

    private double currentLowerY() {
      return currentLowerY;
    }

    private double currentUpperY() {
      return currentUpperY;
    }

    private double targetLowerY() {
      return targetLowerY;
    }

    private double targetUpperY() {
      return targetUpperY;
    }

    private boolean outsideY(double y) {
      return (currentLowerY != ArenaVerticalBoundary.DISABLED && y < currentLowerY)
          || (currentUpperY != ArenaVerticalBoundary.DISABLED && y > currentUpperY);
    }

    private boolean hasTarget() {
      return targetXDistance > 0.0 && targetZDistance > 0.0;
    }

    private void setCurrent(double xDistance, double zDistance, double lowerY, double upperY) {
      this.currentXDistance = Math.max(1.0, xDistance);
      this.currentZDistance = Math.max(1.0, zDistance);
      this.currentLowerY = lowerY;
      this.currentUpperY = upperY;
    }

    private void setTarget(double xDistance, double zDistance, double lowerY, double upperY) {
      this.targetXDistance = Math.max(1.0, xDistance);
      this.targetZDistance = Math.max(1.0, zDistance);
      this.targetLowerY = lowerY;
      this.targetUpperY = upperY;
    }

    private void clearTarget() {
      this.targetXDistance = -1.0;
      this.targetZDistance = -1.0;
      this.targetLowerY = ArenaVerticalBoundary.DISABLED;
      this.targetUpperY = ArenaVerticalBoundary.DISABLED;
    }
  }

  private record BlockSnapshot(Block block, Material material) {
  }

  private record ActivePotionProjectile(String arenaId, ArenaPotionItemConfig config,
      String shooterName, String itemName) {
  }

  private record DeathCredit(String killerName, DeathSource source, String sourceName,
      long expiresAtMillis) {
  }

  private record RecentCreeperPlacement(String playerName, Location location,
      long createdAtMillis) {
  }

  private enum DeathSource {
    PLAYER, TNT, CREEPER, POTION
  }
}
