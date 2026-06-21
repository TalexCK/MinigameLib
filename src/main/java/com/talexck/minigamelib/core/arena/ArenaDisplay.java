package com.talexck.minigamelib.core.arena;

/**
 * Minimal display refresh surface used by gameplay collaborators (e.g. combat) to trigger a
 * scoreboard redraw without depending on the full display implementation.
 */
interface ArenaDisplay {

  /** Re-renders scoreboards for the arena. {@code secondsLeft} is the countdown value, 0 when running. */
  void refreshScoreboards(RuntimeArena arena, int secondsLeft);
}
