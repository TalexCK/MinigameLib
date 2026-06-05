package com.talexck.minigamelib.api.arena;

import java.util.Objects;

public record ArenaTitleConfig(boolean enabled, ArenaTitleFrame teleport,
    ArenaTitleFrame countdownTick, ArenaTitleFrame gameStarted, ArenaTitleFrame gameStopped) {

  public ArenaTitleConfig {
    Objects.requireNonNull(teleport, "teleport");
    Objects.requireNonNull(countdownTick, "countdownTick");
    Objects.requireNonNull(gameStarted, "gameStarted");
    Objects.requireNonNull(gameStopped, "gameStopped");
  }

  public static ArenaTitleConfig disabled() {
    ArenaTitleFrame empty = ArenaTitleFrame.empty();
    return new ArenaTitleConfig(false, empty, empty, empty, empty);
  }
}
