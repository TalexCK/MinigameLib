package com.talexck.minigamelib.core.chest;

import java.util.List;
import java.util.Objects;
import org.bukkit.Material;

public record ChestDefinition(ChestPosition position, List<ChestLootEntry> lootTable,
    ChestPlacementMode placementMode, boolean timedRegeneration, boolean timedDestruction,
    long regenerationPeriodTicks, long destructionDelayTicks, int minItems, int maxItems,
    String displayName, boolean splitStacks, Material blockMaterial, String visualModelKey,
    String openVisualModelKey) {

  public ChestDefinition(ChestPosition position, List<ChestLootEntry> lootTable,
      ChestPlacementMode placementMode, boolean timedRegeneration, boolean timedDestruction,
      long regenerationPeriodTicks, long destructionDelayTicks) {
    this(position, lootTable, placementMode, timedRegeneration, timedDestruction,
        regenerationPeriodTicks, destructionDelayTicks, 0, 27, "", false, Material.CHEST, "", "");
  }

  public ChestDefinition(ChestPosition position, List<ChestLootEntry> lootTable,
      ChestPlacementMode placementMode, boolean timedRegeneration, boolean timedDestruction,
      long regenerationPeriodTicks, long destructionDelayTicks, int minItems, int maxItems) {
    this(position, lootTable, placementMode, timedRegeneration, timedDestruction,
        regenerationPeriodTicks, destructionDelayTicks, minItems, maxItems, "", false,
        Material.CHEST, "", "");
  }

  public ChestDefinition(ChestPosition position, List<ChestLootEntry> lootTable,
      ChestPlacementMode placementMode, boolean timedRegeneration, boolean timedDestruction,
      long regenerationPeriodTicks, long destructionDelayTicks, int minItems, int maxItems,
      String displayName) {
    this(position, lootTable, placementMode, timedRegeneration, timedDestruction,
        regenerationPeriodTicks, destructionDelayTicks, minItems, maxItems, displayName, false,
        Material.CHEST, "", "");
  }

  public ChestDefinition(ChestPosition position, List<ChestLootEntry> lootTable,
      ChestPlacementMode placementMode, boolean timedRegeneration, boolean timedDestruction,
      long regenerationPeriodTicks, long destructionDelayTicks, int minItems, int maxItems,
      String displayName, boolean splitStacks) {
    this(position, lootTable, placementMode, timedRegeneration, timedDestruction,
        regenerationPeriodTicks, destructionDelayTicks, minItems, maxItems, displayName,
        splitStacks, Material.CHEST, "", "");
  }

  public ChestDefinition(ChestPosition position, List<ChestLootEntry> lootTable,
      ChestPlacementMode placementMode, boolean timedRegeneration, boolean timedDestruction,
      long regenerationPeriodTicks, long destructionDelayTicks, int minItems, int maxItems,
      String displayName, boolean splitStacks, String visualModelKey) {
    this(position, lootTable, placementMode, timedRegeneration, timedDestruction,
        regenerationPeriodTicks, destructionDelayTicks, minItems, maxItems, displayName,
        splitStacks, Material.CHEST, visualModelKey, "");
  }

  public ChestDefinition(ChestPosition position, List<ChestLootEntry> lootTable,
      ChestPlacementMode placementMode, boolean timedRegeneration, boolean timedDestruction,
      long regenerationPeriodTicks, long destructionDelayTicks, int minItems, int maxItems,
      String displayName, boolean splitStacks, Material blockMaterial) {
    this(position, lootTable, placementMode, timedRegeneration, timedDestruction,
        regenerationPeriodTicks, destructionDelayTicks, minItems, maxItems, displayName,
        splitStacks, blockMaterial, "", "");
  }

  public ChestDefinition {
    Objects.requireNonNull(position, "position");
    lootTable = List.copyOf(Objects.requireNonNull(lootTable, "lootTable"));
    displayName = displayName == null ? "" : displayName;
    blockMaterial = blockMaterial == null ? Material.CHEST : blockMaterial;
    visualModelKey = visualModelKey == null ? "" : visualModelKey;
    openVisualModelKey = openVisualModelKey == null ? "" : openVisualModelKey;
    if (placementMode == null) {
      placementMode = ChestPlacementMode.AUTO;
    }
    if (regenerationPeriodTicks < 0 || destructionDelayTicks < 0) {
      throw new IllegalArgumentException("chest timings cannot be negative");
    }
    if (minItems < 0 || maxItems < 0 || minItems > maxItems) {
      throw new IllegalArgumentException("chest item counts are invalid");
    }
  }
}
