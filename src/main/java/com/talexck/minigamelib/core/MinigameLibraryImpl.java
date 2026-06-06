package com.talexck.minigamelib.core;

import com.talexck.minigamelib.api.MinigameLibrary;
import com.talexck.minigamelib.api.arena.ArenaService;
import com.talexck.minigamelib.api.lobby.LobbyService;
import com.talexck.minigamelib.api.setup.SetupService;
import com.talexck.minigamelib.core.arena.DefaultArenaService;
import com.talexck.minigamelib.core.lang.LanguageService;
import com.talexck.minigamelib.core.lobby.DefaultLobbyService;
import com.talexck.minigamelib.core.setup.DefaultSetupService;
import com.talexck.minigamelib.core.world.DefaultWorldService;
import org.bukkit.plugin.java.JavaPlugin;

public final class MinigameLibraryImpl implements MinigameLibrary {

  private final DefaultWorldService worldService;
  private final DefaultArenaService arenaService;
  private final DefaultSetupService setupService;
  private final DefaultLobbyService lobbyService;

  public MinigameLibraryImpl(JavaPlugin plugin, LanguageService language) {
    this.worldService = new DefaultWorldService(plugin);
    this.arenaService = new DefaultArenaService(plugin, worldService);
    this.setupService = new DefaultSetupService(plugin, language);
    this.lobbyService = new DefaultLobbyService(plugin);
  }

  @Override
  public ArenaService arenas() {
    return arenaService;
  }

  @Override
  public SetupService setup() {
    return setupService;
  }

  @Override
  public LobbyService lobby() {
    return lobbyService;
  }

  public void shutdown() {
    lobbyService.shutdown();
    setupService.shutdown();
    worldService.shutdown();
    arenaService.shutdown();
  }
}
