package com.talexck.minigamelib.api.arena;

/** How players are spread over the available team colors. */
public enum ArenaTeamFillMode {
  /** Use as many teams as possible, round-robin (the historical behaviour). */
  SPREAD,
  /**
   * Fill each team up to {@link ArenaSettings#maxTeamSize()} before opening the next one (at least
   * two teams whenever two or more players are present). Used for "quads"/"duos" style modes.
   */
  FILL
}
