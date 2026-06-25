package com.talexck.minigamelib.api;

import com.talexck.minigamelib.api.arena.ArenaService;
import com.talexck.minigamelib.api.lobby.LobbyService;
import com.talexck.minigamelib.api.setup.SetupService;
import com.talexck.minigamelib.api.stats.StatsService;

public interface MinigameLibrary {

  ArenaService arenas();

  SetupService setup();

  LobbyService lobby();

  StatsService stats();

}
