package com.talexck.minigamelib.api.arena;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface ArenaService {

  void registerTemplate(ArenaTemplate template);

  boolean unregisterTemplate(String templateId);

  Optional<ArenaTemplate> findTemplate(String templateId);

  CompletableFuture<ArenaHandle> createArena(ArenaCreateRequest request);

  CompletableFuture<Void> startArena(String arenaId);

  CompletableFuture<Void> stopArena(String arenaId, ArenaStopReason reason);

  CompletableFuture<Void> destroyArena(String arenaId);

  CompletableFuture<Void> broadcastMessage(String arenaId, String message);

  CompletableFuture<Void> broadcastMessages(String arenaId, List<String> messages);

  CompletableFuture<Void> sendActionBar(String arenaId, String message);

  CompletableFuture<Void> sendTitle(String arenaId, ArenaTitleFrame title);

  CompletableFuture<Void> playSound(String arenaId, ArenaSound sound);

  CompletableFuture<Void> updateBossBar(String arenaId, String title, double progress);

  Optional<ArenaHandle> findArena(String arenaId);

  List<ArenaHandle> arenas();

  /** The arena (not yet destroyed) the player belongs to, if any. */
  Optional<ArenaHandle> findArenaByPlayer(String playerName);

  /** Whether the player takes part in an arena that is counting down or running. */
  boolean isPlaying(org.bukkit.entity.Player player);
}
