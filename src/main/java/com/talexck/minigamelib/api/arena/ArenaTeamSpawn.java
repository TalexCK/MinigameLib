package com.talexck.minigamelib.api.arena;

import java.util.List;
import java.util.Objects;

public record ArenaTeamSpawn(
    ArenaTeamColor color,
    List<ArenaPoint> spawnPoints) {

  public ArenaTeamSpawn {
    Objects.requireNonNull(color, "color");
    spawnPoints = List.copyOf(Objects.requireNonNull(spawnPoints, "spawnPoints"));
    if (spawnPoints.isEmpty()) {
      throw new IllegalArgumentException("spawnPoints cannot be empty");
    }
  }
}
