package com.talexck.minigamelib.api.arena;

import java.time.Duration;
import java.util.Objects;

public record ArenaBoundaryStage(
    double xDistanceFromCenter,
    double zDistanceFromCenter,
    Duration delayAfterPreviousStage,
    Duration duration) {

  public ArenaBoundaryStage {
    Objects.requireNonNull(delayAfterPreviousStage, "delayAfterPreviousStage");
    Objects.requireNonNull(duration, "duration");
    if (xDistanceFromCenter <= 0 || zDistanceFromCenter <= 0) {
      throw new IllegalArgumentException("stage distance must be positive");
    }
    if (delayAfterPreviousStage.isNegative() || duration.isNegative()) {
      throw new IllegalArgumentException("stage durations cannot be negative");
    }
  }

  public double borderSize() {
    return Math.max(xDistanceFromCenter * 2.0, zDistanceFromCenter * 2.0);
  }
}
