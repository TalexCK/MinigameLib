package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaBoundaryStage;
import com.talexck.minigamelib.api.arena.ArenaBoundaryWall;
import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.arena.ArenaStatus;
import com.talexck.minigamelib.api.arena.ArenaVerticalBoundary;
import com.talexck.minigamelib.core.chest.DefaultChestService;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
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
  private final ConcurrentMap<String, RuntimeBoundary> boundaries = new ConcurrentHashMap<>();

  BoundaryService(JavaPlugin plugin, ArenaRegistry registry, DefaultChestService chestService) {
    this.plugin = plugin;
    this.registry = registry;
    this.chestService = chestService;
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

  /** Starts the per-tick particle render + out-of-bounds damage loop for a running arena. */
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

  @EventHandler
  public void onPlayerMove(PlayerMoveEvent event) {
    if (event.getFrom().getY() == event.getTo().getY()) {
      return;
    }
    Player player = event.getPlayer();
    RuntimeArena arena = registry.findRunningByPlayer(player.getName()).orElse(null);
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
}
