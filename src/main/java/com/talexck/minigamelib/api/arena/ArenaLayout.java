package com.talexck.minigamelib.api.arena;

import java.util.List;
import java.util.Objects;

public record ArenaLayout(
    List<ArenaPoint> spawnPoints,
    List<ArenaPoint> lootChestPoints,
    ArenaPoint center,
    double initialBorderRadius) {

  public ArenaLayout {
    spawnPoints = List.copyOf(Objects.requireNonNull(spawnPoints, "spawnPoints"));
    lootChestPoints = List.copyOf(Objects.requireNonNull(lootChestPoints, "lootChestPoints"));
    Objects.requireNonNull(center, "center");
    if (spawnPoints.isEmpty()) {
      throw new IllegalArgumentException("spawnPoints cannot be empty");
    }
    if (initialBorderRadius <= 0) {
      throw new IllegalArgumentException("initialBorderRadius must be positive");
    }
  }
}
