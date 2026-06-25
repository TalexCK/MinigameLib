package com.talexck.minigamelib.api.stats;

import java.util.Locale;
import java.util.Optional;

public enum LeaderboardType {
  KILLS("kills"),
  WINS("wins"),
  EXPERIENCE("experience");

  private final String key;

  LeaderboardType(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }

  public String displayName() {
    return switch (this) {
      case KILLS -> "击杀";
      case WINS -> "获胜";
      case EXPERIENCE -> "经验值";
    };
  }

  public static Optional<LeaderboardType> fromKey(String key) {
    if (key == null) {
      return Optional.empty();
    }
    return switch (key.toLowerCase(Locale.ROOT)) {
      case "kill", "kills", "击杀" -> Optional.of(KILLS);
      case "win", "wins", "victory", "victories", "获胜", "胜利" -> Optional.of(WINS);
      case "exp", "xp", "experience", "经验", "经验值" -> Optional.of(EXPERIENCE);
      default -> Optional.empty();
    };
  }
}
