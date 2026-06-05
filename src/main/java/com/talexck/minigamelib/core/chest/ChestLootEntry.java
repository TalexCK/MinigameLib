package com.talexck.minigamelib.core.chest;

import com.talexck.minigamelib.api.arena.ArenaItemEntry;

import java.util.List;
import java.util.Objects;

public record ChestLootEntry(List<ArenaItemEntry> items, double weight,
    int earliestGenerationRound) {

  public ChestLootEntry(ArenaItemEntry item, double weight, int earliestGenerationRound) {
    this(List.of(item), weight, earliestGenerationRound);
  }

  public ChestLootEntry {
    items = List.copyOf(Objects.requireNonNull(items, "items"));
    if (items.isEmpty()) {
      throw new IllegalArgumentException("items cannot be empty");
    }
    if (weight <= 0) {
      throw new IllegalArgumentException("weight must be positive");
    }
    if (earliestGenerationRound < 0) {
      throw new IllegalArgumentException("earliestGenerationRound cannot be negative");
    }
  }
}
