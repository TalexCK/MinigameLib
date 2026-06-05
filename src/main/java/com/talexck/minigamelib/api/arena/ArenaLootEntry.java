package com.talexck.minigamelib.api.arena;

import java.util.List;
import java.util.Objects;

public record ArenaLootEntry(
    List<ArenaItemEntry> items,
    double weight,
    int earliestGenerationRound) {

  public ArenaLootEntry(ArenaItemEntry item, double weight, int earliestGenerationRound) {
    this(List.of(item), weight, earliestGenerationRound);
  }

  public ArenaLootEntry {
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
