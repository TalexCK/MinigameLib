package com.talexck.minigamelib.api.arena;

import java.util.Objects;

public record ArenaLootEntry(
    ArenaItemEntry item,
    double weight,
    int earliestGenerationRound) {

  public ArenaLootEntry {
    Objects.requireNonNull(item, "item");
    if (weight <= 0) {
      throw new IllegalArgumentException("weight must be positive");
    }
    if (earliestGenerationRound < 0) {
      throw new IllegalArgumentException("earliestGenerationRound cannot be negative");
    }
  }
}
