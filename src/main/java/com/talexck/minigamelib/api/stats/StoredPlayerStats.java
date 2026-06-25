package com.talexck.minigamelib.api.stats;

import java.util.UUID;

public record StoredPlayerStats(
    UUID playerId,
    String playerName,
    int kills,
    int wins,
    int experience) {
}
