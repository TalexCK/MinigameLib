package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaStopReason;

import java.util.concurrent.CompletableFuture;

/**
 * Lifecycle control surface that collaborators (e.g. combat victory checks) use to request an
 * arena stop without depending on the full controller. Breaks the controller&lt;-&gt;service cycle.
 */
interface ArenaLifecycleControl {

  CompletableFuture<Void> stop(String arenaId, ArenaStopReason reason);
}
