package com.talexck.minigamelib.api.queue;

public enum QueueJoinResult {
  JOINED,
  /** The player was already queued and moved from another queue into this one. */
  SWITCHED,
  ALREADY_QUEUED,
  FULL,
  IN_GAME,
  UNKNOWN_QUEUE
}
