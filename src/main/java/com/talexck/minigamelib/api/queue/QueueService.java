package com.talexck.minigamelib.api.queue;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Lobby matchmaking: players join a queue, a countdown starts once enough players are waiting and
 * the handler receives the queued players when it ends. Queue status is shown on the action bar.
 */
public interface QueueService {

  /** Registers (or replaces, keeping its members) a queue. */
  void register(QueueSettings settings, QueueStartHandler handler);

  boolean unregister(String queueId);

  Collection<QueueSettings> queues();

  QueueJoinResult join(Player player, String queueId);

  /** Removes the player from whatever queue they are in. */
  boolean leave(Player player);

  Optional<QueueSettings> queueOf(Player player);

  List<Player> members(String queueId);

  /** Seconds left in the queue countdown, or -1 while waiting for players. */
  int secondsLeft(String queueId);

  /** Starts the queue now with whoever is waiting (at least one player). */
  boolean forceStart(String queueId);
}
