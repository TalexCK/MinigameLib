package com.talexck.minigamelib.core.chest;

import com.talexck.minigamelib.api.arena.ArenaItemEntry;

import java.util.Objects;

public record ChestLootEntry(ArenaItemEntry item, double weight,
    int earliestGenerationRound) {

  public ChestLootEntry {
    Objects.requireNonNull(item, "item");
    if (weight <= 0) {
      throw new IllegalArgumentException("weight must be positive");
    }
    if (earliestGenerationRound < 0) {
      throw new IllegalArgumentException("earliestGenerationRound cannot be negative");
    }
  }
}
