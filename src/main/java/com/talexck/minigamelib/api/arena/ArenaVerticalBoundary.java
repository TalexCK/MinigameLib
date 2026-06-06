package com.talexck.minigamelib.api.arena;

public record ArenaVerticalBoundary(double lowerY, double upperY) {

  public static final double DISABLED = -1.0;

  public ArenaVerticalBoundary {
    if (lowerY != DISABLED && upperY != DISABLED && lowerY > upperY) {
      throw new IllegalArgumentException("lowerY cannot be greater than upperY");
    }
  }

  public boolean lowerEnabled() {
    return lowerY != DISABLED;
  }

  public boolean upperEnabled() {
    return upperY != DISABLED;
  }

  public boolean enabled() {
    return lowerEnabled() || upperEnabled();
  }

  public boolean outside(double y) {
    return (lowerEnabled() && y < lowerY) || (upperEnabled() && y > upperY);
  }
}
