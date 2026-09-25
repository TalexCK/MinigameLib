package com.talexck.minigamelib.core.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.talexck.minigamelib.api.arena.ArenaBoundaryShape;
import com.talexck.minigamelib.api.arena.ArenaVerticalBoundary;
import org.junit.jupiter.api.Test;

class BoundaryMathTest {

  @Test
  void lerpInterpolatesLinearly() {
    assertEquals(0.0, BoundaryMath.lerp(0.0, 10.0, 0.0));
    assertEquals(5.0, BoundaryMath.lerp(0.0, 10.0, 0.5));
    assertEquals(10.0, BoundaryMath.lerp(0.0, 10.0, 1.0));
    assertEquals(-5.0, BoundaryMath.lerp(0.0, -10.0, 0.5));
  }

  @Test
  void lerpBoundaryYInterpolatesWhenBothEndpointsEnabled() {
    assertEquals(64.0, BoundaryMath.lerpBoundaryY(0.0, 128.0, 0.5));
  }

  @Test
  void lerpBoundaryYReturnsTargetWhenStartDisabled() {
    double result = BoundaryMath.lerpBoundaryY(ArenaVerticalBoundary.DISABLED, 100.0, 0.5);
    assertEquals(100.0, result);
  }

  @Test
  void lerpBoundaryYReturnsTargetWhenEndDisabled() {
    double result = BoundaryMath.lerpBoundaryY(50.0, ArenaVerticalBoundary.DISABLED, 0.5);
    assertEquals(ArenaVerticalBoundary.DISABLED, result);
  }

  @Test
  void circleBoundaryExcludesCornersThatRectangleIncludes() {
    assertFalse(BoundaryMath.outsideHorizontal(ArenaBoundaryShape.RECTANGLE, 9, 9, 10, 10));
    assertTrue(BoundaryMath.outsideHorizontal(ArenaBoundaryShape.CIRCLE, 9, 9, 10, 10));
    assertFalse(BoundaryMath.outsideHorizontal(ArenaBoundaryShape.CIRCLE, 6, 6, 10, 10));
  }

  @Test
  void distanceOutsideIsZeroInsideAndPositiveOutside() {
    assertEquals(0.0, BoundaryMath.distanceOutside(ArenaBoundaryShape.CIRCLE, 3, 4, 10, 10));
    assertEquals(5.0, BoundaryMath.distanceOutside(ArenaBoundaryShape.CIRCLE, 9, 12, 10, 10),
        1e-9);
    assertEquals(2.0, BoundaryMath.distanceOutside(ArenaBoundaryShape.RECTANGLE, 12, 0, 10, 10),
        1e-9);
  }
}
