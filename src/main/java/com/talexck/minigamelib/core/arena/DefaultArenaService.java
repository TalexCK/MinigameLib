package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaCreateRequest;
import com.talexck.minigamelib.api.arena.ArenaHandle;
import com.talexck.minigamelib.api.arena.ArenaService;
import com.talexck.minigamelib.api.arena.ArenaSound;
import com.talexck.minigamelib.api.arena.ArenaStopReason;
import com.talexck.minigamelib.api.arena.ArenaTitleFrame;
import com.talexck.minigamelib.api.arena.ArenaTemplate;
import com.talexck.minigamelib.core.world.DefaultWorldService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class DefaultArenaService implements ArenaService {

  private final ArenaController controller;

  public DefaultArenaService(JavaPlugin plugin, DefaultWorldService worldService) {
    this.controller = new ArenaController(plugin, worldService);
  }

  @Override
  public void registerTemplate(ArenaTemplate template) {
    controller.registerTemplate(template);
  }

  @Override
  public boolean unregisterTemplate(String templateId) {
    return controller.unregisterTemplate(templateId);
  }

  @Override
  public Optional<ArenaTemplate> findTemplate(String templateId) {
    return controller.findTemplate(templateId);
  }

  @Override
  public CompletableFuture<ArenaHandle> createArena(ArenaCreateRequest request) {
    return controller.createArena(request);
  }

  @Override
  public CompletableFuture<Void> startArena(String arenaId) {
    return controller.startArena(arenaId);
  }

  @Override
  public CompletableFuture<Void> stopArena(String arenaId, ArenaStopReason reason) {
    return controller.stopArena(arenaId, reason);
  }

  @Override
  public CompletableFuture<Void> destroyArena(String arenaId) {
    return controller.destroyArena(arenaId);
  }

  @Override
  public CompletableFuture<Void> broadcastMessage(String arenaId, String message) {
    return controller.broadcastMessage(arenaId, message);
  }

  @Override
  public CompletableFuture<Void> broadcastMessages(String arenaId, List<String> messages) {
    return controller.broadcastMessages(arenaId, messages);
  }

  @Override
  public CompletableFuture<Void> sendActionBar(String arenaId, String message) {
    return controller.sendActionBar(arenaId, message);
  }

  @Override
  public CompletableFuture<Void> sendTitle(String arenaId, ArenaTitleFrame title) {
    return controller.sendTitle(arenaId, title);
  }

  @Override
  public CompletableFuture<Void> playSound(String arenaId, ArenaSound sound) {
    return controller.playSound(arenaId, sound);
  }

  @Override
  public CompletableFuture<Void> updateBossBar(String arenaId, String title, double progress) {
    return controller.updateBossBar(arenaId, title, progress);
  }

  @Override
  public Optional<ArenaHandle> findArena(String arenaId) {
    return controller.findArena(arenaId);
  }

  @Override
  public List<ArenaHandle> arenas() {
    return controller.arenas();
  }

  public void shutdown() {
    controller.shutdown();
  }
}
