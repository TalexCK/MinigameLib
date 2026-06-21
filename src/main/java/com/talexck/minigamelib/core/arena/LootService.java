package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaLootChest;
import com.talexck.minigamelib.api.arena.ArenaLootEntry;
import com.talexck.minigamelib.api.arena.ArenaLootPlacementMode;
import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.core.chest.ChestDefinition;
import com.talexck.minigamelib.core.chest.ChestLootEntry;
import com.talexck.minigamelib.core.chest.ChestPlacementMode;
import com.talexck.minigamelib.core.chest.ChestPosition;
import com.talexck.minigamelib.core.chest.DefaultChestService;

import java.util.List;

/** Bridges an arena's configured loot chests into the chest subsystem and starts their lifecycle. */
final class LootService {

  private final DefaultChestService chestService;

  LootService(DefaultChestService chestService) {
    this.chestService = chestService;
  }

  void start(RuntimeArena arena) {
    List<ChestDefinition> definitions =
        arena.settings().lootChests().isEmpty() ? defaultLootChestDefinitions(arena)
            : arena.settings().lootChests().stream().map(this::toChestDefinition).toList();

    chestService.startArenaChests(arena.arenaId(), arena.world().world(), definitions,
        (definition, location) -> {
          ChestPosition position = definition.position();
          ArenaPoint point = new ArenaPoint(position.x(), position.y(), position.z(), 0f, 0f);
          arena.listener().onLootChestGenerated(arena.handle(), point, location);
        });
  }

  private List<ChestDefinition> defaultLootChestDefinitions(RuntimeArena arena) {
    return arena.layout().lootChestPoints().stream()
        .map(point -> new ChestDefinition(new ChestPosition(point.x(), point.y(), point.z()),
            List.of(), ChestPlacementMode.AUTO, false, false, 0L, 0L))
        .toList();
  }

  private ChestDefinition toChestDefinition(ArenaLootChest chest) {
    ArenaPoint point = chest.position();
    return new ChestDefinition(new ChestPosition(point.x(), point.y(), point.z()),
        chest.lootTable().stream().map(this::toChestLootEntry).toList(),
        toChestPlacementMode(chest.placementMode()), chest.timedRegeneration(),
        chest.timedDestruction(), chest.regenerationPeriodTicks(), chest.destructionDelayTicks(),
        chest.minItems(), chest.maxItems(), chest.displayName(), chest.splitStacks(),
        chest.blockMaterial(), chest.visualModelKey(), chest.openVisualModelKey());
  }

  private ChestLootEntry toChestLootEntry(ArenaLootEntry entry) {
    return new ChestLootEntry(entry.items(), entry.weight(), entry.earliestGenerationRound());
  }

  private ChestPlacementMode toChestPlacementMode(ArenaLootPlacementMode mode) {
    return switch (mode) {
      case AUTO -> ChestPlacementMode.AUTO;
      case CENTER -> ChestPlacementMode.CENTER;
      case MIRRORED -> ChestPlacementMode.MIRRORED;
    };
  }
}
