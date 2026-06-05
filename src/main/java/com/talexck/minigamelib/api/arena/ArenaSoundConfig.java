package com.talexck.minigamelib.api.arena;

public record ArenaSoundConfig(
    boolean enabled,
    ArenaSound teleport,
    ArenaSound countdownTick,
    ArenaSound gameStarted,
    ArenaSound gameStopped) {

  public static ArenaSoundConfig disabled() {
    return new ArenaSoundConfig(false, null, null, null, null);
  }
}
