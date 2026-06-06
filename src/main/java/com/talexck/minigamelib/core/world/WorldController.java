package com.talexck.minigamelib.core.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WorldController {

  private final JavaPlugin plugin;
  private final WorldDirectoryRepository repository;
  private final WorldOperationView view;
  private final ExecutorService ioExecutor;

  public WorldController(JavaPlugin plugin, WorldDirectoryRepository repository,
      WorldOperationView view) {
    this.plugin = plugin;
    this.repository = repository;
    this.view = view;
    this.ioExecutor = Executors.newSingleThreadExecutor(command -> {
      Thread thread = new Thread(command, "minigamelib-world-io");
      thread.setDaemon(true);
      return thread;
    });
  }

  public CompletableFuture<WorldCopyResult> copyTemplateWorld(WorldCreateRequest request) {
    return CompletableFuture.supplyAsync(() -> {
      Instant startedAt = Instant.now();
      WorldCopyResult result = repository.copyTemplateWorld(request, startedAt);
      view.renderCopyResult(result);
      return result;
    }, ioExecutor);
  }

  public CompletableFuture<World> loadWorld(String worldName) {
    Instant startedAt = Instant.now();
    CompletableFuture<World> future = new CompletableFuture<>();

    Bukkit.getScheduler().runTask(plugin, () -> {
      try {
        World world = Bukkit.createWorld(new WorldCreator(worldName));
        if (world == null) {
          future
              .completeExceptionally(new IllegalStateException("World load failed: " + worldName));
          return;
        }
        view.renderLoadResult(worldName, Duration.between(startedAt, Instant.now()));
        future.complete(world);
      } catch (RuntimeException exception) {
        future.completeExceptionally(exception);
      }
    });

    return future;
  }

  public CompletableFuture<RuntimeWorld> createRuntimeWorld(WorldCreateRequest request) {
    return copyTemplateWorld(request).thenCompose(copyResult -> {
      Instant loadStartedAt = Instant.now();
      return loadWorld(request.runtimeWorldName()).thenApply(
          world -> new RuntimeWorld(request.templateWorldName(), request.runtimeWorldName(), world,
              copyResult.duration(), Duration.between(loadStartedAt, Instant.now())));
    });
  }

  public boolean unloadWorld(World world, boolean save) {
    boolean unloaded = Bukkit.unloadWorld(world, save);
    view.renderUnloadResult(world.getName(), unloaded);
    return unloaded;
  }

  public CompletableFuture<Boolean> deleteWorldDirectory(String worldName) {
    return CompletableFuture.supplyAsync(() -> repository.deleteWorldDirectory(worldName),
        ioExecutor);
  }

  public void shutdown() {
    ioExecutor.shutdownNow();
  }
}
