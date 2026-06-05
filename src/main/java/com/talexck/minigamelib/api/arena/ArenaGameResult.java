package com.talexck.minigamelib.api.arena;

import java.util.List;

public record ArenaGameResult(
    String arenaId,
    ArenaTeamColor winningTeam,
    List<ArenaTeamStats> teamStats,
    List<ArenaPlayerStats> playerStats,
    ArenaStopReason reason) {
}
