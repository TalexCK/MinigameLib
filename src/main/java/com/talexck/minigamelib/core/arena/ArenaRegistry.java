package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaStatus;
import org.bukkit.World;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * Owns the set of live {@link RuntimeArena} instances and the queries over them. Shared by the
 * controller and every arena collaborator that needs to resolve an arena from a player or world.
 */
final class ArenaRegistry {

  private final ConcurrentMap<String, RuntimeArena> arenas = new ConcurrentHashMap<>();

  boolean contains(String arenaId) {
    return arenas.containsKey(arenaId);
  }

  RuntimeArena get(String arenaId) {
    return arenas.get(arenaId);
  }

  /** Inserts the arena only if absent; returns the previous mapping or {@code null}. */
  RuntimeArena putIfAbsent(RuntimeArena arena) {
    return arenas.putIfAbsent(arena.arenaId(), arena);
  }

  RuntimeArena remove(String arenaId) {
    return arenas.remove(arenaId);
  }

  boolean remove(String arenaId, RuntimeArena arena) {
    return arenas.remove(arenaId, arena);
  }

  List<String> ids() {
    return List.copyOf(arenas.keySet());
  }

  Collection<RuntimeArena> all() {
    return arenas.values();
  }

  Stream<RuntimeArena> stream() {
    return arenas.values().stream();
  }

  Optional<RuntimeArena> findRunningByPlayer(String playerName) {
    return arenas.values().stream()
        .filter(arena -> arena.status() == ArenaStatus.RUNNING)
        .filter(arena -> arena.playerNames().contains(playerName))
        .findFirst();
  }

  Optional<RuntimeArena> findByPlayer(String playerName) {
    return arenas.values().stream()
        .filter(arena -> arena.status() == ArenaStatus.COUNTDOWN
            || arena.status() == ArenaStatus.RUNNING)
        .filter(arena -> arena.playerNames().contains(playerName))
        .findFirst();
  }

  boolean isInActiveArena(String playerName) {
    return arenas.values().stream()
        .filter(arena -> arena.status() == ArenaStatus.COUNTDOWN
            || arena.status() == ArenaStatus.RUNNING || arena.status() == ArenaStatus.STOPPING)
        .anyMatch(arena -> arena.playerNames().contains(playerName));
  }

  /** True when any arena bound to {@code world} is currently counting down or running. */
  boolean hasActiveArenaInWorld(World world) {
    if (world == null) {
      return false;
    }
    return arenas.values().stream().anyMatch(arena -> arena.world().world().equals(world)
        && (arena.status() == ArenaStatus.COUNTDOWN || arena.status() == ArenaStatus.RUNNING));
  }
}
