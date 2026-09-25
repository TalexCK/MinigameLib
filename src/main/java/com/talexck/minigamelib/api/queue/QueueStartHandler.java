package com.talexck.minigamelib.api.queue;

import org.bukkit.entity.Player;

import java.util.List;

/** Called on the main thread when a queue's countdown finishes. */
@FunctionalInterface
public interface QueueStartHandler {

  void start(QueueSettings queue, List<Player> players);
}
