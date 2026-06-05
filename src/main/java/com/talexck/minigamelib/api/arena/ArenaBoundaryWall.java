package com.talexck.minigamelib.api.arena;

public record ArenaBoundaryWall(double x1, double x2, double z1, double z2) {

  public ArenaBoundaryWall {
    if (x1 > x2) {
      throw new IllegalArgumentException("x1 cannot be greater than x2");
    }
    if (z1 > z2) {
      throw new IllegalArgumentException("z1 cannot be greater than z2");
    }
  }

  public double centerX() {
    return (x1 + x2) / 2.0;
  }

  public double centerZ() {
    return (z1 + z2) / 2.0;
  }

  public double size() {
    return Math.max(x2 - x1, z2 - z1);
  }
}
