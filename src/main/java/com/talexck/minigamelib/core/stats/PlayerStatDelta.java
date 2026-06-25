package com.talexck.minigamelib.core.stats;

import java.util.UUID;

record PlayerStatDelta(UUID playerId, String playerName, int kills, int wins, int experience) {
}
