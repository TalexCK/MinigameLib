package com.talexck.minigamelib.core.stats;

import com.talexck.minigamelib.api.stats.LeaderboardEntry;
import com.talexck.minigamelib.api.stats.LeaderboardType;
import com.talexck.minigamelib.api.stats.StatsBoard;
import com.talexck.minigamelib.api.stats.StatsStorageType;
import com.talexck.minigamelib.api.stats.StoredPlayerStats;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class JdbcStatsRepository implements AutoCloseable {

  private final Connection connection;

  JdbcStatsRepository(StatsStorageType storageType, String jdbcUrl, String username,
      String password) throws SQLException, ClassNotFoundException {
    loadDriver(storageType);
    this.connection = username == null || username.isBlank()
        ? DriverManager.getConnection(jdbcUrl)
        : DriverManager.getConnection(jdbcUrl, username, password == null ? "" : password);
    initSchema();
  }

  private void loadDriver(StatsStorageType storageType) throws ClassNotFoundException {
    switch (storageType) {
      case SQLITE -> Class.forName("org.sqlite.JDBC");
      case MYSQL -> Class.forName("com.mysql.cj.jdbc.Driver");
      case POSTGRESQL -> Class.forName("org.postgresql.Driver");
    }
  }

  private void initSchema() throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("""
          CREATE TABLE IF NOT EXISTS player_stats (
            player_uuid VARCHAR(36) PRIMARY KEY,
            player_name VARCHAR(64) NOT NULL,
            kills INTEGER NOT NULL DEFAULT 0,
            wins INTEGER NOT NULL DEFAULT 0,
            experience INTEGER NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL
          )
          """);
      statement.executeUpdate("""
          CREATE TABLE IF NOT EXISTS leaderboard_boards (
            type VARCHAR(32) NOT NULL,
            id VARCHAR(64) NOT NULL,
            world VARCHAR(128) NOT NULL,
            x DOUBLE PRECISION NOT NULL,
            y DOUBLE PRECISION NOT NULL,
            z DOUBLE PRECISION NOT NULL,
            yaw REAL NOT NULL,
            pitch REAL NOT NULL,
            PRIMARY KEY (type, id)
          )
          """);
    }
  }

  synchronized void addPlayerDelta(PlayerStatDelta delta) throws SQLException {
    boolean previousAutoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try {
      if (exists(delta.playerId())) {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE player_stats
            SET player_name = ?, kills = kills + ?, wins = wins + ?, experience = experience + ?,
                updated_at = ?
            WHERE player_uuid = ?
            """)) {
          statement.setString(1, delta.playerName());
          statement.setInt(2, delta.kills());
          statement.setInt(3, delta.wins());
          statement.setInt(4, delta.experience());
          statement.setLong(5, System.currentTimeMillis());
          statement.setString(6, delta.playerId().toString());
          statement.executeUpdate();
        }
      } else {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO player_stats
              (player_uuid, player_name, kills, wins, experience, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """)) {
          statement.setString(1, delta.playerId().toString());
          statement.setString(2, delta.playerName());
          statement.setInt(3, delta.kills());
          statement.setInt(4, delta.wins());
          statement.setInt(5, delta.experience());
          statement.setLong(6, System.currentTimeMillis());
          statement.executeUpdate();
        }
      }
      connection.commit();
    } catch (SQLException exception) {
      connection.rollback();
      throw exception;
    } finally {
      connection.setAutoCommit(previousAutoCommit);
    }
  }

  private boolean exists(UUID playerId) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT 1 FROM player_stats WHERE player_uuid = ?")) {
      statement.setString(1, playerId.toString());
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }

  synchronized Optional<StoredPlayerStats> playerStats(UUID playerId) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
        SELECT player_uuid, player_name, kills, wins, experience
        FROM player_stats WHERE player_uuid = ?
        """)) {
      statement.setString(1, playerId.toString());
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(readStoredStats(resultSet));
      }
    }
  }

  synchronized List<LeaderboardEntry> top(LeaderboardType type, int limit) throws SQLException {
    String column = column(type);
    String sql = "SELECT player_uuid, player_name, kills, wins, experience, " + column
        + " AS board_value FROM player_stats ORDER BY " + column
        + " DESC, player_name ASC LIMIT ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, Math.max(1, limit));
      try (ResultSet resultSet = statement.executeQuery()) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        while (resultSet.next()) {
          entries.add(new LeaderboardEntry(
              UUID.fromString(resultSet.getString("player_uuid")),
              resultSet.getString("player_name"),
              resultSet.getInt("kills"),
              resultSet.getInt("wins"),
              resultSet.getInt("experience"),
              resultSet.getInt("board_value")));
        }
        return List.copyOf(entries);
      }
    }
  }

  synchronized void saveBoard(StatsBoard board) throws SQLException {
    deleteBoard(board.type(), board.id());
    try (PreparedStatement statement = connection.prepareStatement("""
        INSERT INTO leaderboard_boards (type, id, world, x, y, z, yaw, pitch)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """)) {
      statement.setString(1, board.type().key());
      statement.setString(2, board.id());
      statement.setString(3, board.worldName());
      statement.setDouble(4, board.x());
      statement.setDouble(5, board.y());
      statement.setDouble(6, board.z());
      statement.setFloat(7, board.yaw());
      statement.setFloat(8, board.pitch());
      statement.executeUpdate();
    }
  }

  synchronized boolean deleteBoard(LeaderboardType type, String id) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "DELETE FROM leaderboard_boards WHERE type = ? AND id = ?")) {
      statement.setString(1, type.key());
      statement.setString(2, id);
      return statement.executeUpdate() > 0;
    }
  }

  synchronized List<StatsBoard> boards(LeaderboardType type) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
        SELECT type, id, world, x, y, z, yaw, pitch
        FROM leaderboard_boards WHERE type = ? ORDER BY id ASC
        """)) {
      statement.setString(1, type.key());
      try (ResultSet resultSet = statement.executeQuery()) {
        List<StatsBoard> boards = new ArrayList<>();
        while (resultSet.next()) {
          boards.add(readBoard(resultSet));
        }
        return List.copyOf(boards);
      }
    }
  }

  synchronized List<StatsBoard> allBoards() throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
        SELECT type, id, world, x, y, z, yaw, pitch
        FROM leaderboard_boards ORDER BY type ASC, id ASC
        """)) {
      try (ResultSet resultSet = statement.executeQuery()) {
        List<StatsBoard> boards = new ArrayList<>();
        while (resultSet.next()) {
          boards.add(readBoard(resultSet));
        }
        return List.copyOf(boards);
      }
    }
  }

  private StoredPlayerStats readStoredStats(ResultSet resultSet) throws SQLException {
    return new StoredPlayerStats(
        UUID.fromString(resultSet.getString("player_uuid")),
        resultSet.getString("player_name"),
        resultSet.getInt("kills"),
        resultSet.getInt("wins"),
        resultSet.getInt("experience"));
  }

  private StatsBoard readBoard(ResultSet resultSet) throws SQLException {
    LeaderboardType type = LeaderboardType.fromKey(resultSet.getString("type"))
        .orElse(LeaderboardType.KILLS);
    return new StatsBoard(type, resultSet.getString("id"), resultSet.getString("world"),
        resultSet.getDouble("x"), resultSet.getDouble("y"), resultSet.getDouble("z"),
        resultSet.getFloat("yaw"), resultSet.getFloat("pitch"));
  }

  private String column(LeaderboardType type) {
    return switch (type) {
      case KILLS -> "kills";
      case WINS -> "wins";
      case EXPERIENCE -> "experience";
    };
  }

  @Override
  public synchronized void close() throws SQLException {
    connection.close();
  }
}
