package com.talexck.minigamelib.api.stats;

import java.util.Locale;
import java.util.Optional;

public enum StatsStorageType {
  SQLITE,
  MYSQL,
  POSTGRESQL;

  public static Optional<StatsStorageType> fromKey(String key) {
    if (key == null) {
      return Optional.empty();
    }
    return switch (key.toLowerCase(Locale.ROOT)) {
      case "sqlite", "local" -> Optional.of(SQLITE);
      case "mysql", "mariadb" -> Optional.of(MYSQL);
      case "postgres", "postgresql", "pg" -> Optional.of(POSTGRESQL);
      default -> Optional.empty();
    };
  }
}
