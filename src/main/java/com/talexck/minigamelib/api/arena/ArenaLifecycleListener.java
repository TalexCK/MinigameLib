package com.talexck.minigamelib.api.arena;

import org.bukkit.Location;

public interface ArenaLifecycleListener {

  static ArenaLifecycleListener noop() {
    return NoopArenaLifecycleListener.INSTANCE;
  }

  default void onArenaCreated(ArenaHandle arena) {
  }

  default void onBeforeTeleport(ArenaHandle arena) {
  }

  default void onLootChestGenerated(ArenaHandle arena, ArenaPoint point, Location location) {
  }

  default void onCountdownTick(ArenaHandle arena, int secondsLeft) {
  }

  default void onGameStarted(ArenaHandle arena) {
  }

  default void onGameStopped(ArenaHandle arena, ArenaStopReason reason) {
  }

  default void onGameEnded(ArenaHandle arena, ArenaGameResult result) {
  }

  default void onArenaDestroyed(ArenaHandle arena) {
  }
}
