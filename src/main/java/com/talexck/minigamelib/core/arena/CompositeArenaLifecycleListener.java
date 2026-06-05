package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaHandle;
import com.talexck.minigamelib.api.arena.ArenaGameResult;
import com.talexck.minigamelib.api.arena.ArenaLifecycleListener;
import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.arena.ArenaStopReason;
import com.talexck.minigamelib.api.arena.ArenaTeamColor;
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
  public void onKillPlayer(ArenaHandle arena, String killerName, String victimName) {
    listeners.forEach(listener -> listener.onKillPlayer(arena, killerName, victimName));
  }

  @Override
  public void onPlayerKilled(ArenaHandle arena, String playerName, String killerName) {
    listeners.forEach(listener -> listener.onPlayerKilled(arena, playerName, killerName));
  }

  @Override
  public void onPlayerFailed(ArenaHandle arena, String playerName, ArenaTeamColor teamColor) {
    listeners.forEach(listener -> listener.onPlayerFailed(arena, playerName, teamColor));
  }

  @Override
  public void onTeamFailed(ArenaHandle arena, ArenaTeamColor teamColor, List<String> playerNames) {
    listeners.forEach(listener -> listener.onTeamFailed(arena, teamColor, playerNames));
  }

  @Override
  public void onGameStopped(ArenaHandle arena, ArenaStopReason reason) {
    listeners.forEach(listener -> listener.onGameStopped(arena, reason));
  }

  @Override
  public void onGameEnded(ArenaHandle arena, ArenaGameResult result) {
    listeners.forEach(listener -> listener.onGameEnded(arena, result));
  }

  @Override
  public void onArenaDestroyed(ArenaHandle arena) {
    listeners.forEach(listener -> listener.onArenaDestroyed(arena));
  }
}
