package com.talexck.minigamelib.api.lobby;

import com.talexck.minigamelib.api.arena.ArenaPoint;

import java.util.List;

public record LobbySettings(
    String worldName,
    ArenaPoint spawnPoint,
    String scoreboardTitle,
    List<String> scoreboardLines) {

  public LobbySettings(String worldName, ArenaPoint spawnPoint) {
    this(worldName, spawnPoint, "", List.of());
  }

  public LobbySettings {
    scoreboardTitle = scoreboardTitle == null ? "" : scoreboardTitle;
    scoreboardLines =
        scoreboardLines == null ? List.of() : List.copyOf(scoreboardLines);
  }
}
