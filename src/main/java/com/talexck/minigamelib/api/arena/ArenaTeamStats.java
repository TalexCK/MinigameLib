package com.talexck.minigamelib.api.arena;

import java.util.List;

public record ArenaTeamStats(
    ArenaTeamColor color,
    List<String> playerNames,
    int kills,
    int deaths,
    boolean failed) {
}
