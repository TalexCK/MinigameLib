package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaHandle;
import com.talexck.minigamelib.api.arena.ArenaLifecycleListener;
import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.arena.ArenaStopReason;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

final class CompositeArenaLifecycleListener implements ArenaLifecycleListener {

  private final List<ArenaLifecycleListener> listeners;

  private CompositeArenaLifecycleListener(List<ArenaLifecycleListener> listeners) {
    this.listeners = listeners;
  }

  static ArenaLifecycleListener of(ArenaLifecycleListener defaultListener,
      ArenaLifecycleListener requestListener) {
    List<ArenaLifecycleListener> listeners = new ArrayList<>();
    if (defaultListener != null) {
      listeners.add(defaultListener);
    }
    if (requestListener != null) {
      listeners.add(requestListener);
    }
    if (listeners.isEmpty()) {
      return ArenaLifecycleListener.noop();
    }
    return new CompositeArenaLifecycleListener(List.copyOf(listeners));
  }

  @Override
  public void onArenaCreated(ArenaHandle arena) {
    listeners.forEach(listener -> listener.onArenaCreated(arena));
  }

  @Override
  public void onBeforeTeleport(ArenaHandle arena) {
    listeners.forEach(listener -> listener.onBeforeTeleport(arena));
  }

  @Override
  public void onLootChestGenerated(ArenaHandle arena, ArenaPoint point, Location location) {
    listeners.forEach(listener -> listener.onLootChestGenerated(arena, point, location));
  }

  @Override
  public void onCountdownTick(ArenaHandle arena, int secondsLeft) {
    listeners.forEach(listener -> listener.onCountdownTick(arena, secondsLeft));
  }

  @Override
  public void onGameStarted(ArenaHandle arena) {
    listeners.forEach(listener -> listener.onGameStarted(arena));
  }

  @Override
  public void onGameStopped(ArenaHandle arena, ArenaStopReason reason) {
    listeners.forEach(listener -> listener.onGameStopped(arena, reason));
  }

  @Override
  public void onArenaDestroyed(ArenaHandle arena) {
    listeners.forEach(listener -> listener.onArenaDestroyed(arena));
  }
}
