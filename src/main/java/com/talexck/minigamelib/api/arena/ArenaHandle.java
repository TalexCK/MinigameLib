package com.talexck.minigamelib.api.arena;

import java.util.List;

public record ArenaHandle(
    String arenaId,
    String templateId,
    String worldName,
    ArenaStatus status,
    ArenaLayout layout,
    ArenaSettings settings,
    List<String> playerNames) {
}
