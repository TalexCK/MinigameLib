package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaPoint;

import java.util.List;
import java.util.Optional;

/** Pure geometry helpers for team spawn cages, extracted for unit testing. */
final class SpawnGeometry {

  private SpawnGeometry() {
  }

  /** Floors a world coordinate to its containing block coordinate. */
  static int blockCoordinate(double value) {
    return (int) Math.floor(value);
  }

  /**
   * Detects whether the given spawn points form the four corners of a 3x3 square on a single Y
   * level, returning the cage center when they do.
   */
  static Optional<TeamSpawnBounds> teamSpawnBounds(List<ArenaPoint> spawnPoints) {
    if (spawnPoints.size() < 4) {
      return Optional.empty();
    }
    int minX = spawnPoints.stream().mapToInt(point -> blockCoordinate(point.x())).min().orElse(0);
    int maxX = spawnPoints.stream().mapToInt(point -> blockCoordinate(point.x())).max().orElse(0);
    int minY = spawnPoints.stream().mapToInt(point -> blockCoordinate(point.y())).min().orElse(0);
    int maxY = spawnPoints.stream().mapToInt(point -> blockCoordinate(point.y())).max().orElse(0);
    int minZ = spawnPoints.stream().mapToInt(point -> blockCoordinate(point.z())).min().orElse(0);
    int maxZ = spawnPoints.stream().mapToInt(point -> blockCoordinate(point.z())).max().orElse(0);
    if (maxX - minX != 2 || maxZ - minZ != 2 || minY != maxY) {
      return Optional.empty();
    }
    return Optional.of(new TeamSpawnBounds((minX + maxX) / 2, minY, (minZ + maxZ) / 2));
  }

  record TeamSpawnBounds(int centerX, int baseY, int centerZ) {
  }
}
