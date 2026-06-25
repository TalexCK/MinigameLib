package com.talexck.minigamelib.api.stats;

import com.talexck.minigamelib.api.arena.ArenaGameResult;
import org.bukkit.Location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface StatsService {

  void configure(StatsSettings settings);

  boolean isAvailable();

  CompletableFuture<Void> recordGameResult(ArenaGameResult result);

  CompletableFuture<Optional<StoredPlayerStats>> playerStats(UUID playerId);

  CompletableFuture<List<LeaderboardEntry>> top(LeaderboardType type, int limit);

  CompletableFuture<StatsBoard> createBoard(LeaderboardType type, String id, Location location);

  CompletableFuture<Boolean> deleteBoard(LeaderboardType type, String id);

  CompletableFuture<List<StatsBoard>> boards(LeaderboardType type);

  void refreshBoards();
}
