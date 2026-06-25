package com.talexck.minigamelib.api.stats;

import java.util.UUID;

public record LeaderboardEntry(
    UUID playerId,
    String playerName,
    int kills,
    int wins,
    int experience,
    int value) {
}
