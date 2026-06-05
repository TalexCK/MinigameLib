package com.talexck.minigamelib.api.arena;

public record ArenaPlayerStats(
    String playerName,
    ArenaTeamColor teamColor,
    int kills,
    int deaths,
    boolean failed) {
}
