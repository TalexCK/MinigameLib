package com.talexck.minigamelib.api.arena;

import java.util.List;
import java.util.Objects;
import org.bukkit.Material;

public record ArenaLootChest(
    ArenaPoint position,
    List<ArenaLootEntry> lootTable,
    ArenaLootPlacementMode placementMode,
    boolean timedRegeneration,
    boolean timedDestruction,
    long regenerationPeriodTicks,
    long destructionDelayTicks,
    int minItems,
    int maxItems,
    String displayName,
    boolean splitStacks,
    Material blockMaterial,
    String visualModelKey,
    String openVisualModelKey) {

  public ArenaLootChest(ArenaPoint position, List<ArenaLootEntry> lootTable,
      ArenaLootPlacementMode placementMode, boolean timedRegeneration, boolean timedDestruction,
      long regenerationPeriodTicks, long destructionDelayTicks) {
    this(position, lootTable, placementMode, timedRegeneration, timedDestruction,
        regenerationPeriodTicks, destructionDelayTicks, 0, 27, "", false, Material.CHEST, "", "");
  }

  public ArenaLootChest(ArenaPoint position, List<ArenaLootEntry> lootTable,
      ArenaLootPlacementMode placementMode, boolean timedRegeneration, boolean timedDestruction,
      long regenerationPeriodTicks, long destructionDelayTicks, int minItems, int maxItems) {
    this(position, lootTable, placementMode, timedRegeneration, timedDestruction,
        regenerationPeriodTicks, destructionDelayTicks, minItems, maxItems, "", false,
        Material.CHEST, "", "");
  }

  public ArenaLootChest(ArenaPoint position, List<ArenaLootEntry> lootTable,
      ArenaLootPlacementMode placementMode, boolean timedRegeneration, boolean timedDestruction,
      long regenerationPeriodTicks, long destructionDelayTicks, int minItems, int maxItems,
      String displayName) {
    this(position, lootTable, placementMode, timedRegeneration, timedDestruction,
        regenerationPeriodTicks, destructionDelayTicks, minItems, maxItems, displayName, false,
        Material.CHEST, "", "");
  }

  public ArenaLootChest(ArenaPoint position, List<ArenaLootEntry> lootTable,
      ArenaLootPlacementMode placementMode, boolean timedRegeneration, boolean timedDestruction,
      long regenerationPeriodTicks, long destructionDelayTicks, int minItems, int maxItems,
      String displayName, boolean splitStacks) {
    this(position, lootTable, placementMode, timedRegeneration, timedDestruction,
        regenerationPeriodTicks, destructionDelayTicks, minItems, maxItems, displayName,
        splitStacks, Material.CHEST, "", "");
  }

  public ArenaLootChest(ArenaPoint position, List<ArenaLootEntry> lootTable,
      ArenaLootPlacementMode placementMode, boolean timedRegeneration, boolean timedDestruction,
      long regenerationPeriodTicks, long destructionDelayTicks, int minItems, int maxItems,
      String displayName, boolean splitStacks, String visualModelKey) {
    this(position, lootTable, placementMode, timedRegeneration, timedDestruction,
        regenerationPeriodTicks, destructionDelayTicks, minItems, maxItems, displayName,
        splitStacks, Material.CHEST, visualModelKey, "");
  }

  public ArenaLootChest(ArenaPoint position, List<ArenaLootEntry> lootTable,
      ArenaLootPlacementMode placementMode, boolean timedRegeneration, boolean timedDestruction,
      long regenerationPeriodTicks, long destructionDelayTicks, int minItems, int maxItems,
      String displayName, boolean splitStacks, Material blockMaterial) {
    this(position, lootTable, placementMode, timedRegeneration, timedDestruction,
        regenerationPeriodTicks, destructionDelayTicks, minItems, maxItems, displayName,
        splitStacks, blockMaterial, "", "");
  }

  public ArenaLootChest {
    Objects.requireNonNull(position, "position");
    lootTable = List.copyOf(Objects.requireNonNull(lootTable, "lootTable"));
    displayName = displayName == null ? "" : displayName;
    blockMaterial = blockMaterial == null ? Material.CHEST : blockMaterial;
    visualModelKey = visualModelKey == null ? "" : visualModelKey;
    openVisualModelKey = openVisualModelKey == null ? "" : openVisualModelKey;
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
