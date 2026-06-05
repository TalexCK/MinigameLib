package com.talexck.minigamelib.api.arena;

import java.util.List;
import java.util.Objects;

public record ArenaCreateRequest(
    String arenaId,
    String templateId,
    String runtimeWorldName,
    ArenaLayout layout,
    ArenaSettings settings,
    List<String> initialPlayerNames,
    List<ArenaTeam> initialTeams,
    ArenaLifecycleListener listener) {

  public ArenaCreateRequest {
    Objects.requireNonNull(arenaId, "arenaId");
    Objects.requireNonNull(templateId, "templateId");
    Objects.requireNonNull(runtimeWorldName, "runtimeWorldName");
    initialPlayerNames = List.copyOf(Objects.requireNonNull(initialPlayerNames, "initialPlayerNames"));
    initialTeams = List.copyOf(Objects.requireNonNullElse(initialTeams, List.of()));
    if (arenaId.isBlank()) {
      throw new IllegalArgumentException("arenaId cannot be blank");
    }
    if (templateId.isBlank()) {
      throw new IllegalArgumentException("templateId cannot be blank");
    }
    if (runtimeWorldName.isBlank()) {
      throw new IllegalArgumentException("runtimeWorldName cannot be blank");
    }
  }
}
