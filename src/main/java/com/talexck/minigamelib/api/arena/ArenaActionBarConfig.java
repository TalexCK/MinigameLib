package com.talexck.minigamelib.api.arena;

import java.util.Objects;

public record ArenaActionBarConfig(
    boolean enabled,
    String teleport,
    String countdownTick,
    String gameStarted,
    String gameStopped) {

  public ArenaActionBarConfig {
    Objects.requireNonNull(teleport, "teleport");
    Objects.requireNonNull(countdownTick, "countdownTick");
    Objects.requireNonNull(gameStarted, "gameStarted");
    Objects.requireNonNull(gameStopped, "gameStopped");
  }

  public static ArenaActionBarConfig disabled() {
    return new ArenaActionBarConfig(false, "", "", "", "");
  }
}
