package com.talexck.minigamelib.api.arena;

import java.util.List;
import java.util.Objects;

public record ArenaMessages(
    List<String> created,
    List<String> teleport,
    String countdownTick,
    List<String> gameStarted,
    List<String> gameStopped,
    List<String> destroyed) {

  public ArenaMessages {
    created = List.copyOf(Objects.requireNonNull(created, "created"));
    teleport = List.copyOf(Objects.requireNonNull(teleport, "teleport"));
    Objects.requireNonNull(countdownTick, "countdownTick");
    gameStarted = List.copyOf(Objects.requireNonNull(gameStarted, "gameStarted"));
    gameStopped = List.copyOf(Objects.requireNonNull(gameStopped, "gameStopped"));
    destroyed = List.copyOf(Objects.requireNonNull(destroyed, "destroyed"));
  }

  public static ArenaMessages defaults() {
    return new ArenaMessages(
        List.of(),
        List.of(),
        "",
        List.of(),
        List.of(),
        List.of());
  }
}
