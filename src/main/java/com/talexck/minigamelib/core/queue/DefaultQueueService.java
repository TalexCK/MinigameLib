package com.talexck.minigamelib.core.queue;

import com.talexck.minigamelib.api.queue.QueueJoinResult;
import com.talexck.minigamelib.api.queue.QueueService;
import com.talexck.minigamelib.api.queue.QueueSettings;
import com.talexck.minigamelib.api.queue.QueueStartHandler;
import com.talexck.minigamelib.core.lang.LanguageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Main-thread matchmaking queues with an action bar status line. */
public final class DefaultQueueService implements QueueService, Listener {

  private final JavaPlugin plugin;
  private final LanguageService language;
  private final Predicate<Player> inGame;
  private final Map<String, Queue> queues = new LinkedHashMap<>();
  private final BukkitTask ticker;

  public DefaultQueueService(JavaPlugin plugin, LanguageService language,
      Predicate<Player> inGame) {
    this.plugin = plugin;
    this.language = language;
    this.inGame = inGame;
    Bukkit.getPluginManager().registerEvents(this, plugin);
    this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
  }

  @Override
  public void register(QueueSettings settings, QueueStartHandler handler) {
    Objects.requireNonNull(settings, "settings");
    Objects.requireNonNull(handler, "handler");
    Queue existing = queues.get(settings.id());
    Queue queue = new Queue(settings, handler);
    if (existing != null) {
      queue.members.addAll(existing.members);
    }
    queues.put(settings.id(), queue);
  }

  @Override
  public boolean unregister(String queueId) {
    Queue removed = queues.remove(queueId);
    if (removed == null) {
      return false;
    }
    for (UUID member : removed.members) {
      Player player = Bukkit.getPlayer(member);
      if (player != null) {
        send(player, language.text("queue.cancelled", "{queue}", removed.settings.displayName()));
      }
    }
    return true;
  }

  @Override
  public Collection<QueueSettings> queues() {
    return queues.values().stream().map(queue -> queue.settings).toList();
  }

  @Override
  public QueueJoinResult join(Player player, String queueId) {
    Queue queue = queues.get(queueId);
    if (queue == null) {
      return QueueJoinResult.UNKNOWN_QUEUE;
    }
    if (inGame.test(player)) {
      return QueueJoinResult.IN_GAME;
    }
    Queue current = find(player.getUniqueId());
    if (current == queue) {
      return QueueJoinResult.ALREADY_QUEUED;
    }
    if (queue.members.size() >= queue.settings.maxPlayers()) {
      return QueueJoinResult.FULL;
    }
    if (current != null) {
      current.members.remove(player.getUniqueId());
      current.resetIfNeeded();
    }
    queue.members.add(player.getUniqueId());
    broadcast(queue, language.text("queue.joined", "{player}", player.getName(),
        "{queue}", queue.settings.displayName(), "{count}", queue.members.size(),
        "{max}", queue.settings.maxPlayers()));
    playAll(queue, Sound.BLOCK_NOTE_BLOCK_PLING, 1.4f);
    updateCountdown(queue);
    return current == null ? QueueJoinResult.JOINED : QueueJoinResult.SWITCHED;
  }

  @Override
  public boolean leave(Player player) {
    Queue queue = find(player.getUniqueId());
    if (queue == null) {
      return false;
    }
    queue.members.remove(player.getUniqueId());
    player.sendActionBar(Component.empty());
    broadcast(queue, language.text("queue.left", "{player}", player.getName(),
        "{queue}", queue.settings.displayName(), "{count}", queue.members.size(),
        "{max}", queue.settings.maxPlayers()));
    queue.resetIfNeeded();
    return true;
  }

  @Override
  public Optional<QueueSettings> queueOf(Player player) {
    return Optional.ofNullable(find(player.getUniqueId())).map(queue -> queue.settings);
  }

  @Override
  public List<Player> members(String queueId) {
    Queue queue = queues.get(queueId);
    return queue == null ? List.of() : onlineMembers(queue);
  }

  @Override
  public int secondsLeft(String queueId) {
    Queue queue = queues.get(queueId);
    return queue == null ? -1 : queue.secondsLeft;
  }

  @Override
  public boolean forceStart(String queueId) {
    Queue queue = queues.get(queueId);
    if (queue == null || queue.members.isEmpty()) {
      return false;
    }
    start(queue);
    return true;
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    Queue queue = find(event.getPlayer().getUniqueId());
    if (queue != null) {
      queue.members.remove(event.getPlayer().getUniqueId());
      queue.resetIfNeeded();
    }
  }

  private void tick() {
    for (Queue queue : List.copyOf(queues.values())) {
      queue.members.removeIf(member -> {
        Player player = Bukkit.getPlayer(member);
        return player == null || inGame.test(player);
      });
      queue.resetIfNeeded();
      if (queue.secondsLeft > 0) {
        queue.secondsLeft--;
        if (queue.secondsLeft == 0) {
          start(queue);
          continue;
        }
        if (queue.secondsLeft <= 5) {
          playAll(queue, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f + (5 - queue.secondsLeft) * 0.15f);
        }
      }
      for (Player player : onlineMembers(queue)) {
        player.sendActionBar(color(status(queue)));
      }
    }
  }

  private String status(Queue queue) {
    if (queue.secondsLeft > 0) {
      return language.text("queue.status-starting", "{queue}", queue.settings.displayName(),
          "{count}", queue.members.size(), "{max}", queue.settings.maxPlayers(),
          "{seconds}", queue.secondsLeft);
    }
    return language.text("queue.status-waiting", "{queue}", queue.settings.displayName(),
        "{count}", queue.members.size(), "{max}", queue.settings.maxPlayers(),
        "{needed}", Math.max(0, queue.settings.minPlayers() - queue.members.size()));
  }

  private void updateCountdown(Queue queue) {
    if (queue.members.size() < queue.settings.minPlayers()) {
      return;
    }
    int target = queue.members.size() >= queue.settings.maxPlayers()
        ? queue.settings.fullCountdownSeconds() : queue.settings.countdownSeconds();
    if (queue.secondsLeft <= 0 || queue.secondsLeft > target) {
      queue.secondsLeft = target;
    }
  }

  private void start(Queue queue) {
    List<Player> players = onlineMembers(queue);
    queue.members.clear();
    queue.secondsLeft = -1;
    if (players.isEmpty()) {
      return;
    }
    for (Player player : players) {
      player.sendActionBar(Component.empty());
    }
    try {
      queue.handler.start(queue.settings, players);
    } catch (RuntimeException exception) {
      plugin.getLogger().warning("Queue " + queue.settings.id() + " failed to start: "
          + exception.getMessage());
      players.forEach(player -> send(player, language.text("queue.start-failed",
          "{error}", String.valueOf(exception.getMessage()))));
    }
  }

  private Queue find(UUID playerId) {
    return queues.values().stream().filter(queue -> queue.members.contains(playerId)).findFirst()
        .orElse(null);
  }

  private List<Player> onlineMembers(Queue queue) {
    List<Player> players = new ArrayList<>();
    for (UUID member : queue.members) {
      Player player = Bukkit.getPlayer(member);
      if (player != null) {
        players.add(player);
      }
    }
    return players;
  }

  private void broadcast(Queue queue, String message) {
    onlineMembers(queue).forEach(player -> send(player, message));
  }

  private void playAll(Queue queue, Sound sound, float pitch) {
    onlineMembers(queue).forEach(player -> player.playSound(player.getLocation(), sound, 0.8f,
        pitch));
  }

  private void send(Player player, String message) {
    player.sendMessage(color(message));
  }

  private Component color(String text) {
    return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
  }

  public void shutdown() {
    ticker.cancel();
    HandlerList.unregisterAll(this);
    queues.clear();
  }

  private final class Queue {
    private final QueueSettings settings;
    private final QueueStartHandler handler;
    private final Set<UUID> members = new LinkedHashSet<>();
    private int secondsLeft = -1;

    private Queue(QueueSettings settings, QueueStartHandler handler) {
      this.settings = settings;
      this.handler = handler;
    }

    private void resetIfNeeded() {
      if (secondsLeft > 0 && members.size() < settings.minPlayers()) {
        secondsLeft = -1;
        broadcast(this, language.text("queue.countdown-cancelled", "{queue}",
            settings.displayName()));
      } else {
        updateCountdown(this);
      }
    }
  }
}
