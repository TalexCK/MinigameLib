package com.talexck.minigamelib.core;

import com.talexck.minigamelib.api.MinigameLibrary;
import com.talexck.minigamelib.api.arena.ArenaService;
import com.talexck.minigamelib.core.arena.DefaultArenaService;
import com.talexck.minigamelib.core.world.DefaultWorldService;
import org.bukkit.plugin.java.JavaPlugin;

public final class MinigameLibraryImpl implements MinigameLibrary {

  private final DefaultWorldService worldService;
  private final DefaultArenaService arenaService;

  public MinigameLibraryImpl(JavaPlugin plugin) {
    this.worldService = new DefaultWorldService(plugin);
    this.arenaService = new DefaultArenaService(plugin, worldService);
  }

  @Override
  public ArenaService arenas() {
    return arenaService;
  }

  public void shutdown() {
    worldService.shutdown();
    arenaService.shutdown();
  }
}
