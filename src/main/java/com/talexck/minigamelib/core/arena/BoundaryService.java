package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaBoundaryShape;
import com.talexck.minigamelib.api.arena.ArenaBoundaryStage;
import com.talexck.minigamelib.api.arena.ArenaBoundaryWall;
import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.arena.ArenaStatus;
import com.talexck.minigamelib.api.arena.ArenaVerticalBoundary;
import com.talexck.minigamelib.core.chest.DefaultChestService;
import com.talexck.minigamelib.core.lang.LanguageService;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages the shrinking play boundary for each arena: initial bounds, scheduled shrink stages,
 * boundary particles, out-of-bounds damage, and protecting arena chests from explosions.
 */
final class BoundaryService implements Listener {

  private final JavaPlugin plugin;
  private final ArenaRegistry registry;
  private final DefaultChestService chestService;
  private final CombatService combatService;
  private final LanguageService language;
  private static final double VIEW_DISTANCE = 20.0;
  private static final double STEP = 1.5;
  private static final Particle.DustOptions CURRENT_DUST =
      new Particle.DustOptions(Color.fromRGB(255, 60, 60), 1.4f);
  private static final Particle.DustOptions TARGET_DUST =
      new Particle.DustOptions(Color.fromRGB(255, 170, 0), 1.1f);
  private final ConcurrentMap<String, RuntimeBoundary> boundaries = new ConcurrentHashMap<>();

  BoundaryService(JavaPlugin plugin, ArenaRegistry registry, DefaultChestService chestService,
      CombatService combatService, LanguageService language) {
    this.plugin = plugin;
    this.language = language;
    this.registry = registry;
    this.chestService = chestService;
    this.combatService = combatService;
    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  /** Establishes the arena's initial boundary from its wall config or layout border radius. */
  void init(RuntimeArena arena) {
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

  /** Forgets the arena's boundary state. */
  void remove(String arenaId) {
    boundaries.remove(arenaId);
  }

  void cancelTasks(RuntimeArena arena) {
    arena.boundaryTasks().forEach(BukkitTask::cancel);
    arena.boundaryTasks().clear();
  }

  /** Starts the particle render + out-of-bounds damage loop for a running arena. */
  void startLifecycle(RuntimeArena arena) {
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

  /** Schedules each configured shrink stage relative to game start. */
  void scheduleStages(RuntimeArena arena) {
    RuntimeBoundary boundary = boundaries.get(arena.arenaId());
    long now = System.currentTimeMillis();
    long delayTicks = 0L;
    for (ArenaBoundaryStage stage : arena.settings().boundaryStages()) {
      delayTicks += toTicks(stage.delayAfterPreviousStage());
      long durationTicks = toTicks(stage.duration());
      if (boundary != null) {
        boundary.windows().add(new long[] {now + delayTicks * 50L,
            now + (delayTicks + durationTicks) * 50L});
      }
      BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
          () -> startBoundaryShrink(arena, stage, durationTicks), delayTicks);
      arena.boundaryTasks().add(task);
      delayTicks += durationTicks;
    }
  }

  /** Human readable boundary state for the {@code {border}} placeholder. */
  String statusText(RuntimeArena arena) {
    RuntimeBoundary boundary = boundaries.get(arena.arenaId());
    if (boundary == null || boundary.windows().isEmpty()) {
      return language.text("border.static");
    }
    if (arena.status() != ArenaStatus.RUNNING) {
      return language.text("border.waiting");
    }
    long now = System.currentTimeMillis();
    for (long[] window : boundary.windows()) {
      if (now < window[0]) {
        return language.text("border.next", "{time}",
            DisplayService.formatTime((window[0] - now + 999L) / 1000L));
      }
      if (now < window[1]) {
        return language.text("border.shrinking", "{time}",
            DisplayService.formatTime((window[1] - now + 999L) / 1000L));
      }
    }
    return language.text("border.final");
  }

  private void startBoundaryShrink(RuntimeArena arena, ArenaBoundaryStage stage,
      long durationTicks) {
    RuntimeBoundary boundary = boundaries.get(arena.arenaId());
    if (boundary == null || arena.status() != ArenaStatus.RUNNING) {
      return;
    }
    announceShrink(arena);
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
        boundary.setCurrent(BoundaryMath.lerp(startX, targetX, progress),
            BoundaryMath.lerp(startZ, targetZ, progress),
            BoundaryMath.lerpBoundaryY(startLowerY, targetLowerY, progress),
            BoundaryMath.lerpBoundaryY(startUpperY, targetUpperY, progress));
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

  private void announceShrink(RuntimeArena arena) {
    String message = arena.settings().presentation().borderShrinkMessage();
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null) {
        continue;
      }
      if (!message.isBlank()) {
        player.sendMessage(LegacyText.component(message));
      }
      player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.6f);
    }
  }

  private void spawnBoundaryParticles(RuntimeArena arena, RuntimeBoundary boundary) {
    World world = arena.world().world();
    ArenaBoundaryShape shape = arena.settings().rules().boundaryShape();
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null || !player.getWorld().equals(world)) {
        continue;
      }
      Location eye = player.getLocation();
      // Only the part of the boundary near each viewer is drawn, and only for that viewer.
      spawnWallNear(player, shape, boundary, boundary.currentXDistance(),
          boundary.currentZDistance(), eye, CURRENT_DUST, 3);
      if (boundary.hasTarget()) {
        spawnWallNear(player, shape, boundary, boundary.targetXDistance(),
            boundary.targetZDistance(), eye, TARGET_DUST, 1);
      }
      spawnHorizontalPlaneNear(player, shape, boundary, boundary.currentLowerY(), eye);
      spawnHorizontalPlaneNear(player, shape, boundary, boundary.currentUpperY(), eye);
    }
  }

  private void spawnWallNear(Player viewer, ArenaBoundaryShape shape, RuntimeBoundary boundary,
      double xDistance, double zDistance, Location eye, Particle.DustOptions dust, int rows) {
    double dx = eye.getX() - boundary.centerX();
    double dz = eye.getZ() - boundary.centerZ();
    double gap = BoundaryMath.distanceOutside(shape, dx, dz, xDistance, zDistance);
    boolean inside = !BoundaryMath.outsideHorizontal(shape, dx, dz, xDistance, zDistance);
    if (inside) {
      // Distance from the wall when inside.
      gap = shape == ArenaBoundaryShape.CIRCLE
          ? Math.max(0.0, Math.min(xDistance, zDistance) - Math.sqrt(dx * dx + dz * dz))
          : Math.min(xDistance - Math.abs(dx), zDistance - Math.abs(dz));
    }
    if (gap > VIEW_DISTANCE) {
      return;
    }
    for (double[] point : wallPoints(shape, boundary, xDistance, zDistance, eye)) {
      for (int row = 0; row < rows; row++) {
        double y = eye.getY() - 0.5 + row * 1.2;
        viewer.spawnParticle(Particle.DUST, point[0], y, point[1], 1, 0.0, 0.0, 0.0, 0.0, dust);
      }
    }
  }

  private List<double[]> wallPoints(ArenaBoundaryShape shape, RuntimeBoundary boundary,
      double xDistance, double zDistance, Location eye) {
    List<double[]> points = new java.util.ArrayList<>();
    double cx = boundary.centerX();
    double cz = boundary.centerZ();
    double maxDistanceSquared = VIEW_DISTANCE * VIEW_DISTANCE;
    if (shape == ArenaBoundaryShape.CIRCLE) {
      double radius = Math.max(xDistance, zDistance);
      int samples = (int) Math.min(720, Math.max(24, Math.ceil(2 * Math.PI * radius / STEP)));
      for (int index = 0; index < samples; index++) {
        double angle = 2 * Math.PI * index / samples;
        double x = cx + Math.cos(angle) * xDistance;
        double z = cz + Math.sin(angle) * zDistance;
        if (square(x - eye.getX()) + square(z - eye.getZ()) <= maxDistanceSquared) {
          points.add(new double[] {x, z});
        }
      }
      return points;
    }
    double minX = cx - xDistance;
    double maxX = cx + xDistance;
    double minZ = cz - zDistance;
    double maxZ = cz + zDistance;
    for (double x = minX; x <= maxX; x += STEP) {
      addIfNear(points, x, minZ, eye, maxDistanceSquared);
      addIfNear(points, x, maxZ, eye, maxDistanceSquared);
    }
    for (double z = minZ; z <= maxZ; z += STEP) {
      addIfNear(points, minX, z, eye, maxDistanceSquared);
      addIfNear(points, maxX, z, eye, maxDistanceSquared);
    }
    return points;
  }

  private void addIfNear(List<double[]> points, double x, double z, Location eye,
      double maxDistanceSquared) {
    if (square(x - eye.getX()) + square(z - eye.getZ()) <= maxDistanceSquared) {
      points.add(new double[] {x, z});
    }
  }

  /** Draws a small patch of the floor/ceiling boundary around a viewer close to it. */
  private void spawnHorizontalPlaneNear(Player viewer, ArenaBoundaryShape shape,
      RuntimeBoundary boundary, double y, Location eye) {
    if (y == ArenaVerticalBoundary.DISABLED || Math.abs(eye.getY() - y) > 10.0) {
      return;
    }
    for (double x = -8; x <= 8; x += STEP) {
      for (double z = -8; z <= 8; z += STEP) {
        double px = Math.floor(eye.getX()) + x;
        double pz = Math.floor(eye.getZ()) + z;
        if (BoundaryMath.outsideHorizontal(shape, px - boundary.centerX(),
            pz - boundary.centerZ(), boundary.currentXDistance(), boundary.currentZDistance())) {
          continue;
        }
        viewer.spawnParticle(Particle.DUST, px, y, pz, 1, 0.0, 0.0, 0.0, 0.0, CURRENT_DUST);
      }
    }
  }

  private static double square(double value) {
    return value * value;
  }

  private void damagePlayersOutsideBoundary(RuntimeArena arena, RuntimeBoundary boundary) {
    ArenaBoundaryShape shape = arena.settings().rules().boundaryShape();
    double damage = arena.settings().rules().boundaryDamagePerSecond();
    String warning = arena.settings().presentation().outOfBoundsActionBar();
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null || arena.isFailed(playerName)
          || !player.getWorld().equals(arena.world().world())) {
        continue;
      }
      Location location = player.getLocation();
      boolean outside = BoundaryMath.outsideHorizontal(shape,
          location.getX() - boundary.centerX(), location.getZ() - boundary.centerZ(),
          boundary.currentXDistance(), boundary.currentZDistance())
          || boundary.outsideY(location.getY());
      if (!outside) {
        continue;
      }
      if (!warning.isBlank()) {
        player.sendActionBar(LegacyText.component(warning));
      }
      player.playSound(location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.5f);
      if (damage > 0) {
        player.damage(damage);
      }
    }
  }

  /**
   * Falling below the world (or below the configured lower boundary) eliminates immediately. The
   * upper boundary only hurts over time, like the sides.
   */
  @EventHandler
  public void onPlayerMove(PlayerMoveEvent event) {
    if (event.getFrom().getBlockY() == event.getTo().getBlockY()) {
      return;
    }
    Player player = event.getPlayer();
    RuntimeArena arena = registry.findRunningByPlayer(player.getName()).orElse(null);
    if (arena == null || arena.isFailed(player.getName())) {
      return;
    }
    double y = event.getTo().getY();
    RuntimeBoundary runtimeBoundary = boundaries.get(arena.arenaId());
    boolean belowConfigured = runtimeBoundary != null
        && runtimeBoundary.currentLowerY() != ArenaVerticalBoundary.DISABLED
        && y < runtimeBoundary.currentLowerY() - 8.0;
    boolean belowWorld = y < player.getWorld().getMinHeight();
    if (!belowConfigured && !belowWorld) {
      return;
    }
    combatService.eliminatePlayer(player);
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

  private boolean protectArenaExplosion(World world, List<Block> blocks) {
    if (!registry.hasActiveArenaInWorld(world)) {
      return false;
    }
    blocks.removeIf(block -> block.getType() == Material.CHEST
        || block.getType() == Material.TRAPPED_CHEST || chestService.isActiveChest(block));
    return true;
  }

  void shutdown() {
    HandlerList.unregisterAll(this);
    boundaries.clear();
  }

  private static long toTicks(Duration duration) {
    return Math.max(0L, duration.toMillis() / 50L);
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
    private final List<long[]> windows = new java.util.ArrayList<>();

    private RuntimeBoundary(double centerX, double centerZ, double currentXDistance,
        double currentZDistance, double currentLowerY, double currentUpperY) {
      this.centerX = centerX;
      this.centerZ = centerZ;
      this.currentXDistance = Math.max(1.0, currentXDistance);
      this.currentZDistance = Math.max(1.0, currentZDistance);
      this.currentLowerY = currentLowerY;
      this.currentUpperY = currentUpperY;
    }

    private List<long[]> windows() {
      return windows;
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
}
