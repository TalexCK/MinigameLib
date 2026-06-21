package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaStopReason;

/** Renders an arena text template with placeholders substituted. Implemented by DisplayService. */
@FunctionalInterface
interface ArenaTextRenderer {

  String render(RuntimeArena arena, String text, int secondsLeft, ArenaStopReason reason);
}
