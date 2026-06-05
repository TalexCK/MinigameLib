package com.talexck.minigamelib.core.chest;

import java.util.List;
import java.util.Objects;

public record ChestDefinition(ChestPosition position, List<ChestLootEntry> lootTable,
    ChestPlacementMode placementMode, boolean timedRegeneration, boolean timedDestruction,
    long regenerationPeriodTicks, long destructionDelayTicks) {

  public ChestDefinition {
    Objects.requireNonNull(position, "position");
    lootTable = List.copyOf(Objects.requireNonNull(lootTable, "lootTable"));
    if (placementMode == null) {
      placementMode = ChestPlacementMode.AUTO;
    }
    if (regenerationPeriodTicks < 0 || destructionDelayTicks < 0) {
      throw new IllegalArgumentException("chest timings cannot be negative");
    }
  }
}
