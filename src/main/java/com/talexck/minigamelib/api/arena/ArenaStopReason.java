package com.talexck.minigamelib.api.arena;

public enum ArenaStopReason {
  NORMAL,
  /** The configured {@link ArenaRules#timeLimit()} was reached. Counts as a normal finish. */
  TIME_UP,
  FORCE,
  ERROR;

  /** Whether the game finished by its own rules (results should be recorded). */
  public boolean finishedNaturally() {
    return this == NORMAL || this == TIME_UP;
  }
}
