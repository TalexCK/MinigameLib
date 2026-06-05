package com.talexck.minigamelib.api.arena;

import java.util.List;
import java.util.Objects;

public record ArenaTeam(
    ArenaTeamColor color,
    List<String> playerNames) {

  public ArenaTeam {
    Objects.requireNonNull(color, "color");
    playerNames = List.copyOf(Objects.requireNonNull(playerNames, "playerNames"));
  }
}
