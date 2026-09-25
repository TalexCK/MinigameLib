package com.talexck.minigamelib.api.arena;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Game rules shared by elimination style minigames.
 *
 * @param timeLimit round length, {@link Duration#ZERO} for unlimited. When the time runs out the
 *     game ends and the surviving teams are ranked by alive players, then score.
 * @param killScore score awarded to the credited killer.
 * @param outliveScore score awarded to every still-alive player when an enemy player is eliminated.
 * @param placementScores score awarded to each member of the team finishing at index {@code i}.
 * @param teamFillMode how players are distributed over teams.
 * @param boundaryShape horizontal shape of the play boundary.
 * @param boundaryDamagePerSecond damage dealt each second while outside the boundary.
 * @param postGameSeconds how long players stay in the arena after the game ends.
 */
public record ArenaRules(
    Duration timeLimit,
    int killScore,
    int outliveScore,
    List<Integer> placementScores,
    ArenaTeamFillMode teamFillMode,
    ArenaBoundaryShape boundaryShape,
    double boundaryDamagePerSecond,
    int postGameSeconds) {

  public ArenaRules {
    timeLimit = Objects.requireNonNullElse(timeLimit, Duration.ZERO);
    placementScores = List.copyOf(Objects.requireNonNullElse(placementScores, List.of()));
    teamFillMode = Objects.requireNonNullElse(teamFillMode, ArenaTeamFillMode.SPREAD);
    boundaryShape = Objects.requireNonNullElse(boundaryShape, ArenaBoundaryShape.RECTANGLE);
    if (timeLimit.isNegative()) {
      throw new IllegalArgumentException("timeLimit cannot be negative");
    }
    if (killScore < 0 || outliveScore < 0) {
      throw new IllegalArgumentException("scores cannot be negative");
    }
    if (boundaryDamagePerSecond < 0) {
      throw new IllegalArgumentException("boundaryDamagePerSecond cannot be negative");
    }
    postGameSeconds = Math.max(0, postGameSeconds);
  }

  /** Rules matching the library behaviour before rules existed. */
  public static ArenaRules defaults() {
    return new ArenaRules(Duration.ZERO, 0, 0, List.of(), ArenaTeamFillMode.SPREAD,
        ArenaBoundaryShape.RECTANGLE, 2.0, 15);
  }

  public boolean hasTimeLimit() {
    return !timeLimit.isZero();
  }

  /** Placement bonus for a zero-based team placement index. */
  public int placementScore(int placementIndex) {
    return placementIndex >= 0 && placementIndex < placementScores.size()
        ? placementScores.get(placementIndex) : 0;
  }
}
