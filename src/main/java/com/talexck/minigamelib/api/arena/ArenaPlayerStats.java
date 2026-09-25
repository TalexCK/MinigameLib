package com.talexck.minigamelib.api.arena;

public record ArenaPlayerStats(
    String playerName,
    ArenaTeamColor teamColor,
    int kills,
    int deaths,
    boolean failed,
    int score) {

  public ArenaPlayerStats(String playerName, ArenaTeamColor teamColor, int kills, int deaths,
      boolean failed) {
    this(playerName, teamColor, kills, deaths, failed, 0);
  }
}
