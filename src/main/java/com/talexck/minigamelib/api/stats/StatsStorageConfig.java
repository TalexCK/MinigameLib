package com.talexck.minigamelib.api.stats;

public record StatsStorageConfig(
    StatsStorageType type,
    String sqliteFile,
    String jdbcUrl,
    String username,
    String password) {

  public StatsStorageConfig {
    type = type == null ? StatsStorageType.SQLITE : type;
    sqliteFile = sqliteFile == null || sqliteFile.isBlank() ? "stats.db" : sqliteFile;
    jdbcUrl = jdbcUrl == null ? "" : jdbcUrl;
    username = username == null ? "" : username;
    password = password == null ? "" : password;
  }

  public static StatsStorageConfig sqlite(String sqliteFile) {
    return new StatsStorageConfig(StatsStorageType.SQLITE, sqliteFile, "", "", "");
  }
}
