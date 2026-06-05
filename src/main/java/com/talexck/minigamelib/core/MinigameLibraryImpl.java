package com.talexck.minigamelib.core;

import com.talexck.minigamelib.api.MinigameLibrary;
import com.talexck.minigamelib.api.arena.ArenaService;
import com.talexck.minigamelib.api.setup.SetupService;
import com.talexck.minigamelib.core.arena.DefaultArenaService;
import com.talexck.minigamelib.core.lang.LanguageService;
import com.talexck.minigamelib.core.setup.DefaultSetupService;
import com.talexck.minigamelib.core.world.DefaultWorldService;
import org.bukkit.plugin.java.JavaPlugin;

public final class MinigameLibraryImpl implements MinigameLibrary {

  private final DefaultWorldService worldService;
  private final DefaultArenaService arenaService;
  private final DefaultSetupService setupService;

  public MinigameLibraryImpl(JavaPlugin plugin, LanguageService language) {
    this.worldService = new DefaultWorldService(plugin);
    this.arenaService = new DefaultArenaService(plugin, worldService);
    this.setupService = new DefaultSetupService(plugin, language);
  }

  @Override
  public ArenaService arenas() {
    return arenaService;
  }

  @Override
  public SetupService setup() {
    return setupService;
  }

  public void shutdown() {
    setupService.shutdown();
    worldService.shutdown();
    arenaService.shutdown();
  }
}
