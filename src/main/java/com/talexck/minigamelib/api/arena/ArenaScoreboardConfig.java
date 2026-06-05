package com.talexck.minigamelib.api.arena;

import java.util.List;
import java.util.Objects;

public record ArenaScoreboardConfig(
    boolean enabled,
    String title,
    List<String> lines) {

  public ArenaScoreboardConfig {
    Objects.requireNonNull(title, "title");
    lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
  }

  public static ArenaScoreboardConfig disabled() {
    return new ArenaScoreboardConfig(false, "", List.of());
  }
}
