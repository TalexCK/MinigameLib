package com.talexck.minigamelib.core.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
