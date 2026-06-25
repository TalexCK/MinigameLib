package com.talexck.minigamelib.api.stats;

public record StatsSettings(
    StatsStorageConfig storage,
    int killExperience,
    int winExperience) {

  public StatsSettings {
    storage = storage == null ? StatsStorageConfig.sqlite("stats.db") : storage;
    killExperience = Math.max(0, killExperience);
    winExperience = Math.max(0, winExperience);
  }

  public static StatsSettings defaults(String sqliteFile) {
    return new StatsSettings(StatsStorageConfig.sqlite(sqliteFile), 10, 50);
  }
}
