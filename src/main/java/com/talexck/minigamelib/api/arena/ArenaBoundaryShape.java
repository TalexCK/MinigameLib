package com.talexck.minigamelib.api.arena;

/** Horizontal shape of the shrinking play boundary. */
public enum ArenaBoundaryShape {
  /** Axis-aligned rectangle using the x/z distances as half extents. */
  RECTANGLE,
  /** Circle (or ellipse when the x/z distances differ) around the boundary center. */
  CIRCLE
}
