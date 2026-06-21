package com.talexck.minigamelib.core.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.core.arena.SpawnGeometry.TeamSpawnBounds;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpawnGeometryTest {

  @Test
  void floorsCoordinate() {
    assertEquals(3, SpawnGeometry.blockCoordinate(3.9));
    assertEquals(-4, SpawnGeometry.blockCoordinate(-3.1));
  }

  @Test
  void detectsThreeByThreeSquareCorners() {
    List<ArenaPoint> corners = List.of(
        point(0, 64, 0),
        point(2, 64, 0),
        point(0, 64, 2),
        point(2, 64, 2));
    Optional<TeamSpawnBounds> bounds = SpawnGeometry.teamSpawnBounds(corners);
    assertTrue(bounds.isPresent());
    assertEquals(1, bounds.get().centerX());
    assertEquals(1, bounds.get().centerZ());
    assertEquals(64, bounds.get().baseY());
  }

  @Test
  void rejectsFewerThanFourPoints() {
    assertTrue(SpawnGeometry.teamSpawnBounds(List.of(point(0, 64, 0), point(2, 64, 2))).isEmpty());
  }

  @Test
  void rejectsNonSquareSpan() {
    List<ArenaPoint> corners = List.of(
        point(0, 64, 0),
        point(3, 64, 0),
        point(0, 64, 2),
        point(3, 64, 2));
    assertTrue(SpawnGeometry.teamSpawnBounds(corners).isEmpty());
  }

  @Test
  void rejectsPointsOnDifferentYLevels() {
    List<ArenaPoint> corners = List.of(
        point(0, 64, 0),
        point(2, 64, 0),
        point(0, 65, 2),
        point(2, 64, 2));
    assertTrue(SpawnGeometry.teamSpawnBounds(corners).isEmpty());
  }

  private static ArenaPoint point(double x, double y, double z) {
    return new ArenaPoint(x, y, z, 0f, 0f);
  }
}
