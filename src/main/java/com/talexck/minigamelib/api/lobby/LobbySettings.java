package com.talexck.minigamelib.api.lobby;

import com.talexck.minigamelib.api.arena.ArenaPoint;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Lobby configuration.
 *
 * @param hotbarItems items placed in the given hotbar slots (0-8) whenever a player enters the
 *     lobby. Players cannot drop or move them.
 * @param placeholders extra placeholder resolver applied to scoreboard lines after the built-in
 *     ones (e.g. queue status). May be {@code null}.
 */
public record LobbySettings(
    String worldName,
    ArenaPoint spawnPoint,
    String scoreboardTitle,
    List<String> scoreboardLines,
    Map<Integer, ItemStack> hotbarItems,
    java.util.function.BiFunction<org.bukkit.entity.Player, String, String> placeholders) {

  public LobbySettings(String worldName, ArenaPoint spawnPoint) {
    this(worldName, spawnPoint, "", List.of());
  }

  public LobbySettings(String worldName, ArenaPoint spawnPoint, String scoreboardTitle,
      List<String> scoreboardLines) {
    this(worldName, spawnPoint, scoreboardTitle, scoreboardLines, Map.of(), null);
  }

  public LobbySettings {
    scoreboardTitle = scoreboardTitle == null ? "" : scoreboardTitle;
    scoreboardLines =
        scoreboardLines == null ? List.of() : List.copyOf(scoreboardLines);
    hotbarItems = hotbarItems == null ? Map.of() : Map.copyOf(hotbarItems);
    if (placeholders == null) {
      placeholders = (player, text) -> text;
    }
  }
}
