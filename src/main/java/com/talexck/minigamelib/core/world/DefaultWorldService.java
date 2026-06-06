package com.talexck.minigamelib.core.world;

import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public final class DefaultWorldService {

  private final WorldController controller;

  public DefaultWorldService(JavaPlugin plugin) {
    this.controller = new WorldController(plugin,
        new WorldDirectoryRepository(plugin.getServer().getWorldContainer().toPath()),
        new WorldOperationView(plugin.getLogger()));
  }

  public CompletableFuture<WorldCopyResult> copyTemplateWorld(WorldCreateRequest request) {
    return controller.copyTemplateWorld(request);
  }

  public CompletableFuture<World> loadWorld(String worldName) {
    return controller.loadWorld(worldName);
  }

  public CompletableFuture<RuntimeWorld> createRuntimeWorld(WorldCreateRequest request) {
    return controller.createRuntimeWorld(request);
  }

  public boolean unloadWorld(World world, boolean save) {
    return controller.unloadWorld(world, save);
  }

  public CompletableFuture<Boolean> deleteWorldDirectory(String worldName) {
    return controller.deleteWorldDirectory(worldName);
  }

  public void shutdown() {
    controller.shutdown();
  }
}
