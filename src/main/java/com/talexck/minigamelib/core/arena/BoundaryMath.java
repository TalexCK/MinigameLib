package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaBoundaryShape;
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

  /**
   * Whether an offset {@code (dx, dz)} from the boundary center lies outside the horizontal
   * boundary with half extents {@code xDistance}/{@code zDistance}.
   */
  static boolean outsideHorizontal(ArenaBoundaryShape shape, double dx, double dz,
      double xDistance, double zDistance) {
    if (shape == ArenaBoundaryShape.CIRCLE) {
      double nx = dx / Math.max(0.0001, xDistance);
      double nz = dz / Math.max(0.0001, zDistance);
      return nx * nx + nz * nz > 1.0;
    }
    return Math.abs(dx) > xDistance || Math.abs(dz) > zDistance;
  }

  /**
   * Distance a point must travel to get back inside the boundary, 0 when inside. For circles this
   * is measured along the radius (exact for circles, approximate for ellipses).
   */
  static double distanceOutside(ArenaBoundaryShape shape, double dx, double dz, double xDistance,
      double zDistance) {
    if (shape == ArenaBoundaryShape.CIRCLE) {
      double distance = Math.sqrt(dx * dx + dz * dz);
      if (distance == 0.0) {
        return 0.0;
      }
      double nx = dx / Math.max(0.0001, xDistance);
      double nz = dz / Math.max(0.0001, zDistance);
      double normalized = Math.sqrt(nx * nx + nz * nz);
      return normalized <= 1.0 ? 0.0 : distance - distance / normalized;
    }
    double outsideX = Math.max(0.0, Math.abs(dx) - xDistance);
    double outsideZ = Math.max(0.0, Math.abs(dz) - zDistance);
    return Math.sqrt(outsideX * outsideX + outsideZ * outsideZ);
  }
}
