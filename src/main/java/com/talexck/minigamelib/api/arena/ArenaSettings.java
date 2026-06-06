package com.talexck.minigamelib.api.arena;

import java.util.List;
import java.util.Objects;

public record ArenaSettings(
    int countdownSeconds,
    String returnWorldName,
    ArenaPoint returnPoint,
    boolean saveWorldOnUnload,
    ArenaScoreboardConfig scoreboard,
    ArenaBossBarConfig bossBar,
    ArenaActionBarConfig actionBar,
    ArenaTitleConfig title,
    ArenaSoundConfig sounds,
    ArenaResourcePackConfig resourcePack,
    List<ArenaItemEntry> beginningItems,
    List<ArenaLootChest> lootChests,
    ArenaBoundaryWall initialBoundaryWall,
    ArenaVerticalBoundary verticalBoundary,
    List<ArenaBoundaryStage> boundaryStages,
    ArenaVictoryCondition victoryCondition,
    ArenaMessages messages) {

  public ArenaSettings(int countdownSeconds, String returnWorldName, ArenaPoint returnPoint,
      boolean saveWorldOnUnload, ArenaScoreboardConfig scoreboard, ArenaBossBarConfig bossBar,
      ArenaActionBarConfig actionBar, ArenaTitleConfig title, ArenaSoundConfig sounds,
      ArenaResourcePackConfig resourcePack, List<ArenaItemEntry> beginningItems,
      List<ArenaLootChest> lootChests, ArenaBoundaryWall initialBoundaryWall,
      List<ArenaBoundaryStage> boundaryStages, ArenaVictoryCondition victoryCondition,
      ArenaMessages messages) {
    this(countdownSeconds, returnWorldName, returnPoint, saveWorldOnUnload, scoreboard, bossBar,
        actionBar, title, sounds, resourcePack, beginningItems, lootChests, initialBoundaryWall,
        new ArenaVerticalBoundary(ArenaVerticalBoundary.DISABLED, ArenaVerticalBoundary.DISABLED),
        boundaryStages, victoryCondition, messages);
  }

  public ArenaSettings {
    Objects.requireNonNull(returnWorldName, "returnWorldName");
    Objects.requireNonNull(returnPoint, "returnPoint");
    beginningItems = List.copyOf(Objects.requireNonNullElse(beginningItems, List.of()));
    lootChests = List.copyOf(Objects.requireNonNullElse(lootChests, List.of()));
    boundaryStages = List.copyOf(Objects.requireNonNullElse(boundaryStages, List.of()));
    if (victoryCondition == null) {
      victoryCondition = ArenaVictoryCondition.OTHER_TEAMS_ALL_FAILED;
    }
    if (scoreboard == null) {
      scoreboard = ArenaScoreboardConfig.disabled();
    }
    if (bossBar == null) {
      bossBar = ArenaBossBarConfig.disabled();
    }
    if (actionBar == null) {
      actionBar = ArenaActionBarConfig.disabled();
    }
    if (title == null) {
      title = ArenaTitleConfig.disabled();
    }
    if (sounds == null) {
      sounds = ArenaSoundConfig.disabled();
    }
    if (resourcePack == null) {
      resourcePack = ArenaResourcePackConfig.disabled();
    }
    if (messages == null) {
      messages = ArenaMessages.defaults();
    }
    if (verticalBoundary == null) {
      verticalBoundary = new ArenaVerticalBoundary(ArenaVerticalBoundary.DISABLED,
          ArenaVerticalBoundary.DISABLED);
    }
    if (countdownSeconds < 0) {
      throw new IllegalArgumentException("countdownSeconds cannot be negative");
    }
    if (returnWorldName.isBlank()) {
      throw new IllegalArgumentException("returnWorldName cannot be blank");
    }
  }
}
