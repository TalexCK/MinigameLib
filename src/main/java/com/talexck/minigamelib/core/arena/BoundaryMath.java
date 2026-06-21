package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaVerticalBoundary;

/** Pure interpolation helpers for boundary shrinking, extracted for unit testing. */
final class BoundaryMath {

  private BoundaryMath() {
  }

  /** Linear interpolation between {@code start} and {@code end} at {@code progress} in [0, 1]. */
  static double lerp(double start, double end, double progress) {
    return start + (end - start) * progress;
  }

  /**
   * Like {@link #lerp} but treats {@link ArenaVerticalBoundary#DISABLED} as a sentinel: if either
   * endpoint is disabled, the target value is returned unchanged rather than interpolated.
   */
  static double lerpBoundaryY(double start, double end, double progress) {
    if (start == ArenaVerticalBoundary.DISABLED || end == ArenaVerticalBoundary.DISABLED) {
      return end;
    }
    return lerp(start, end, progress);
  }
}
