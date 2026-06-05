package com.talexck.minigamelib.api.arena;

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
    ArenaMessages messages) {

  public ArenaSettings {
    Objects.requireNonNull(returnWorldName, "returnWorldName");
    Objects.requireNonNull(returnPoint, "returnPoint");
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
    if (messages == null) {
      messages = ArenaMessages.defaults();
    }
    if (countdownSeconds < 0) {
      throw new IllegalArgumentException("countdownSeconds cannot be negative");
    }
    if (returnWorldName.isBlank()) {
      throw new IllegalArgumentException("returnWorldName cannot be blank");
    }
  }
}
