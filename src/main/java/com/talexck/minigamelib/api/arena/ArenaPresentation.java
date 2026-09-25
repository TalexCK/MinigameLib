package com.talexck.minigamelib.api.arena;

import java.util.Objects;

/**
 * Game specific names and feedback texts. Every text supports the arena placeholders plus
 * {@code {victim}}, {@code {killer}} and {@code {gained}} where noted. Blank texts are skipped.
 *
 * @param gameName name shown in the tablist header and end-of-game summary.
 * @param modeName mode shown through {@code {mode}}.
 * @param mapName map shown through {@code {map}}.
 * @param eliminatedTitle title shown to an eliminated player.
 * @param eliminatedSubtitle subtitle shown to an eliminated player ({@code {killer}}).
 * @param killActionBar action bar shown to a killer ({@code {victim}}, {@code {gained}}).
 * @param teamEliminatedMessage chat broadcast when a team is wiped ({@code {team}}).
 * @param victoryTitle title for the winning team.
 * @param victorySubtitle subtitle for the winning team.
 * @param defeatTitle title for every other player.
 * @param defeatSubtitle subtitle for every other player ({@code {winner}}).
 * @param runningBossBar boss bar text while the game runs ({@code {time_left}}, {@code {border}}).
 * @param borderShrinkMessage chat/title text when a boundary stage starts shrinking.
 * @param outOfBoundsActionBar action bar while a player stands outside the boundary.
 * @param timeUpMessage chat broadcast when the time limit is reached.
 */
public record ArenaPresentation(
    String gameName,
    String modeName,
    String mapName,
    String eliminatedTitle,
    String eliminatedSubtitle,
    String killActionBar,
    String teamEliminatedMessage,
    String victoryTitle,
    String victorySubtitle,
    String defeatTitle,
    String defeatSubtitle,
    String runningBossBar,
    String borderShrinkMessage,
    String outOfBoundsActionBar,
    String timeUpMessage) {

  public ArenaPresentation {
    gameName = Objects.requireNonNullElse(gameName, "Minigame");
    modeName = Objects.requireNonNullElse(modeName, "");
    mapName = Objects.requireNonNullElse(mapName, "");
    eliminatedTitle = Objects.requireNonNullElse(eliminatedTitle, "");
    eliminatedSubtitle = Objects.requireNonNullElse(eliminatedSubtitle, "");
    killActionBar = Objects.requireNonNullElse(killActionBar, "");
    teamEliminatedMessage = Objects.requireNonNullElse(teamEliminatedMessage, "");
    victoryTitle = Objects.requireNonNullElse(victoryTitle, "");
    victorySubtitle = Objects.requireNonNullElse(victorySubtitle, "");
    defeatTitle = Objects.requireNonNullElse(defeatTitle, "");
    defeatSubtitle = Objects.requireNonNullElse(defeatSubtitle, "");
    runningBossBar = Objects.requireNonNullElse(runningBossBar, "");
    borderShrinkMessage = Objects.requireNonNullElse(borderShrinkMessage, "");
    outOfBoundsActionBar = Objects.requireNonNullElse(outOfBoundsActionBar, "");
    timeUpMessage = Objects.requireNonNullElse(timeUpMessage, "");
  }

  public static ArenaPresentation named(String gameName) {
    return new ArenaPresentation(gameName, "", "", "", "", "", "", "", "", "", "", "", "", "", "");
  }

  public ArenaPresentation withMap(String mapName) {
    return new ArenaPresentation(gameName, modeName, mapName, eliminatedTitle, eliminatedSubtitle,
        killActionBar, teamEliminatedMessage, victoryTitle, victorySubtitle, defeatTitle,
        defeatSubtitle, runningBossBar, borderShrinkMessage, outOfBoundsActionBar, timeUpMessage);
  }
}
