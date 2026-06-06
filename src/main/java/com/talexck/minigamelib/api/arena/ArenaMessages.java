package com.talexck.minigamelib.api.arena;

import java.util.List;
import java.util.Objects;

public record ArenaMessages(
    List<String> created,
    List<String> teleport,
    String countdownTick,
    List<String> gameStarted,
    List<String> gameStopped,
    List<String> destroyed,
    String deathGeneric,
    String deathByPlayer,
    String deathByTnt,
    String deathByCreeper,
    String deathByPotion) {

  public ArenaMessages(List<String> created, List<String> teleport, String countdownTick,
      List<String> gameStarted, List<String> gameStopped, List<String> destroyed) {
    this(created, teleport, countdownTick, gameStarted, gameStopped, destroyed,
        "{victim} 被击败了。", "{victim} 被 {killer} 击败了。",
        "{victim} 被 {killer} 的 TNT 炸飞了。",
        "{victim} 被 {killer} 的苦力怕击败了。",
        "{victim} 被 {killer} 的 {source} 击败了。");
  }

  public ArenaMessages {
    created = List.copyOf(Objects.requireNonNull(created, "created"));
    teleport = List.copyOf(Objects.requireNonNull(teleport, "teleport"));
    Objects.requireNonNull(countdownTick, "countdownTick");
    gameStarted = List.copyOf(Objects.requireNonNull(gameStarted, "gameStarted"));
    gameStopped = List.copyOf(Objects.requireNonNull(gameStopped, "gameStopped"));
    destroyed = List.copyOf(Objects.requireNonNull(destroyed, "destroyed"));
    Objects.requireNonNull(deathGeneric, "deathGeneric");
    Objects.requireNonNull(deathByPlayer, "deathByPlayer");
    Objects.requireNonNull(deathByTnt, "deathByTnt");
    Objects.requireNonNull(deathByCreeper, "deathByCreeper");
    Objects.requireNonNull(deathByPotion, "deathByPotion");
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
