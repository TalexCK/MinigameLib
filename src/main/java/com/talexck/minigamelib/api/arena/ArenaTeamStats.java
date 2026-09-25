package com.talexck.minigamelib.api.arena;

import java.util.List;

public record ArenaTeamStats(
    ArenaTeamColor color,
    List<String> playerNames,
    int kills,
    int deaths,
    boolean failed,
    int score,
    int placement) {

  public ArenaTeamStats(ArenaTeamColor color, List<String> playerNames, int kills, int deaths,
      boolean failed) {
    this(color, playerNames, kills, deaths, failed, 0, 0);
  }
}
