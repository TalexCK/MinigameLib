package com.talexck.minigamelib.api.arena;

import java.util.List;
import java.util.Objects;

public record ArenaLootChest(
    ArenaPoint position,
    List<ArenaLootEntry> lootTable,
    ArenaLootPlacementMode placementMode,
    boolean timedRegeneration,
    boolean timedDestruction,
    long regenerationPeriodTicks,
    long destructionDelayTicks,
    int minItems,
    int maxItems) {

  public ArenaLootChest(ArenaPoint position, List<ArenaLootEntry> lootTable,
      ArenaLootPlacementMode placementMode, boolean timedRegeneration, boolean timedDestruction,
      long regenerationPeriodTicks, long destructionDelayTicks) {
    this(position, lootTable, placementMode, timedRegeneration, timedDestruction,
        regenerationPeriodTicks, destructionDelayTicks, 0, 27);
  }

  public ArenaLootChest {
    Objects.requireNonNull(position, "position");
    lootTable = List.copyOf(Objects.requireNonNull(lootTable, "lootTable"));
    if (placementMode == null) {
      placementMode = ArenaLootPlacementMode.AUTO;
    }
    if (regenerationPeriodTicks < 0 || destructionDelayTicks < 0) {
      throw new IllegalArgumentException("loot chest timings cannot be negative");
    }
    if (minItems < 0 || maxItems < 0 || minItems > maxItems) {
      throw new IllegalArgumentException("loot chest item counts are invalid");
    }
  }
}
