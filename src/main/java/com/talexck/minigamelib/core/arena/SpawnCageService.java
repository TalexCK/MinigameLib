package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaPoint;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Builds and removes the temporary barrier cages that pen players at their spawn during the
 * pre-game countdown, restoring the original blocks afterwards.
 */
final class SpawnCageService {

  private final ConcurrentMap<String, List<BlockSnapshot>> spawnCages = new ConcurrentHashMap<>();

  /**
   * Builds a cage around a team's spawn points. When the points form a 3x3 square a single shared
   * cage is built around their center; otherwise a cage is built around each point.
   */
  void createTeamSpawnCage(String arenaId, World world, List<ArenaPoint> spawnPoints) {
    if (spawnPoints.isEmpty()) {
      return;
    }
    SpawnGeometry.TeamSpawnBounds bounds = SpawnGeometry.teamSpawnBounds(spawnPoints).orElse(null);
    if (bounds == null) {
      for (ArenaPoint point : spawnPoints) {
        createSpawnCage(arenaId, world, SpawnGeometry.blockCoordinate(point.x()),
            SpawnGeometry.blockCoordinate(point.y()), SpawnGeometry.blockCoordinate(point.z()));
      }
      return;
    }
    createSpawnCage(arenaId, world, bounds.centerX(), bounds.baseY(), bounds.centerZ());
  }

  private void createSpawnCage(String arenaId, World world, int centerX, int baseY, int centerZ) {
    List<BlockSnapshot> snapshots =
        spawnCages.computeIfAbsent(arenaId, ignored -> new ArrayList<>());
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

  /** Restores blocks replaced by an arena's cages and forgets the arena. */
  void clear(String arenaId) {
    List<BlockSnapshot> snapshots = spawnCages.remove(arenaId);
    if (snapshots == null) {
      return;
    }
    for (BlockSnapshot snapshot : snapshots) {
      if (snapshot.block().getType() == Material.BARRIER) {
        snapshot.block().setType(snapshot.material(), false);
      }
    }
  }

  private record BlockSnapshot(Block block, Material material) {
  }
}
