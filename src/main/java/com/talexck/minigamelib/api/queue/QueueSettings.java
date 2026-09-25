package com.talexck.minigamelib.api.queue;

import java.util.Objects;

/**
 * A lobby matchmaking queue.
 *
 * @param id unique id, e.g. {@code "skybattle-solo"}.
 * @param displayName name shown in queue messages (legacy {@code &} colours allowed).
 * @param minPlayers players needed before the start countdown begins.
 * @param maxPlayers queue capacity; the game starts sooner once it is full.
 * @param countdownSeconds countdown once {@code minPlayers} is reached.
 * @param fullCountdownSeconds countdown used once the queue is full (never longer than
 *     {@code countdownSeconds}).
 */
public record QueueSettings(
    String id,
    String displayName,
    int minPlayers,
    int maxPlayers,
    int countdownSeconds,
    int fullCountdownSeconds) {

  public QueueSettings {
    Objects.requireNonNull(id, "id");
    displayName = displayName == null || displayName.isBlank() ? id : displayName;
    if (minPlayers < 1 || maxPlayers < minPlayers) {
      throw new IllegalArgumentException("invalid queue player limits");
    }
    countdownSeconds = Math.max(1, countdownSeconds);
    fullCountdownSeconds = Math.max(1, Math.min(countdownSeconds, fullCountdownSeconds));
  }
}
