package com.talexck.minigamelib.core.lobby;

import com.talexck.minigamelib.api.lobby.LobbyService;
import com.talexck.minigamelib.api.lobby.LobbySettings;
import com.talexck.minigamelib.api.stats.StatsService;
import com.talexck.minigamelib.api.stats.StoredPlayerStats;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultLobbyService implements LobbyService, Listener {

  private static final long STATS_REFRESH_INTERVAL_MILLIS = 5_000L;

  private final JavaPlugin plugin;
  private final StatsService stats;
  private LobbySettings settings;
  private BukkitTask hungerTask;
  private final Map<UUID, String> tabScoreboardNames = new ConcurrentHashMap<>();
  private final Map<UUID, StoredPlayerStats> statsCache = new ConcurrentHashMap<>();
  private final Map<UUID, Long> statsRefreshAt = new ConcurrentHashMap<>();
  private final Set<UUID> pendingStatsRequests = ConcurrentHashMap.newKeySet();

  public DefaultLobbyService(JavaPlugin plugin, StatsService stats) {
    this.plugin = plugin;
    this.stats = stats;
    Bukkit.getPluginManager().registerEvents(this, plugin);
    this.hungerTask = new BukkitRunnable() {
      @Override
      public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
          if (isLobbyPlayer(player)) {
            fillFood(player);
            applyLobbyScoreboard(player);
          }
        }
      }
    }.runTaskTimer(plugin, 0L, 20L);
  }

  @Override
  public void configure(LobbySettings settings) {
    this.settings = settings;
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (isLobbyPlayer(player)) {
        applyLobbyScoreboard(player);
      }
    }
  }

  @Override
  public Optional<LobbySettings> settings() {
    return Optional.ofNullable(settings);
  }

  @Override
  public boolean isLobbyWorld(String worldName) {
    return settings != null && settings.worldName().equals(worldName);
  }

  @Override
  public void teleportToSpawn(Player player) {
    if (settings == null) {
      return;
    }
    World world = Bukkit.getWorld(settings.worldName());
    if (world == null) {
      return;
    }
    player.teleport(settings.spawnPoint().toLocation(world));
    player.setGameMode(GameMode.ADVENTURE);
    fillFood(player);
    applyLobbyScoreboard(player);
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    Bukkit.getScheduler().runTask(plugin, () -> teleportToSpawn(event.getPlayer()));
  }

  @EventHandler
  public void onRespawn(PlayerRespawnEvent event) {
    if (settings == null) {
      return;
    }
    World world = Bukkit.getWorld(settings.worldName());
    if (world != null) {
      event.setRespawnLocation(settings.spawnPoint().toLocation(world));
    }
  }

  @EventHandler
  public void onDamage(EntityDamageEvent event) {
    if (event.getEntity() instanceof Player player && isLobbyPlayer(player)) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onFood(FoodLevelChangeEvent event) {
    if (event.getEntity() instanceof Player player && isLobbyPlayer(player)) {
      event.setCancelled(true);
      fillFood(player);
    }
  }

  @EventHandler
  public void onMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    if (!isLobbyPlayer(player) || event.getTo().getY() >= player.getWorld().getMinHeight()) {
      return;
    }
    teleportToSpawn(player);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    removeLobbyScoreboard(playerId);
    statsCache.remove(playerId);
    statsRefreshAt.remove(playerId);
    pendingStatsRequests.remove(playerId);
  }

  public void shutdown() {
    HandlerList.unregisterAll(this);
    if (hungerTask != null) {
      hungerTask.cancel();
      hungerTask = null;
    }
    clearLobbyScoreboard();
  }

  private boolean isLobbyPlayer(Player player) {
    return settings != null && player.getWorld().getName().equals(settings.worldName());
  }

  private void fillFood(Player player) {
    player.setFoodLevel(20);
    player.setSaturation(20.0f);
    player.setExhaustion(0.0f);
  }

  private void applyLobbyScoreboard(Player player) {
    if (settings == null || settings.scoreboardTitle().isBlank()
        || settings.scoreboardLines().isEmpty()) {
      return;
    }
    refreshStats(player);
    try {
      me.neznamy.tab.api.scoreboard.ScoreboardManager manager =
          TabAPI.getInstance().getScoreboardManager();
      if (manager == null) {
        return;
      }
      String name = "mgl-lobby-scoreboard-" + player.getUniqueId();
      me.neznamy.tab.api.scoreboard.Scoreboard scoreboard =
          manager.getRegisteredScoreboards().get(name);
      if (scoreboard == null) {
        scoreboard = manager.createScoreboard(name, color(render(player, settings.scoreboardTitle())),
            settings.scoreboardLines().stream().map(line -> color(render(player, line))).toList());
        tabScoreboardNames.put(player.getUniqueId(), name);
      } else {
        scoreboard.setTitle(color(render(player, settings.scoreboardTitle())));
        scoreboard.setLines(settings.scoreboardLines().stream()
            .map(line -> color(render(player, line))).toList());
      }
      TabPlayer tabPlayer = TabAPI.getInstance().getPlayer(player.getName());
      if (tabPlayer != null && tabPlayer.isLoaded()) {
        manager.showScoreboard(tabPlayer, scoreboard);
      }
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB lobby scoreboard unavailable: " + exception.getMessage());
    }
  }

  private void clearLobbyScoreboard() {
    try {
      me.neznamy.tab.api.scoreboard.ScoreboardManager manager =
          TabAPI.getInstance().getScoreboardManager();
      if (manager != null) {
        for (String name : tabScoreboardNames.values()) {
          if (manager.getRegisteredScoreboards().containsKey(name)) {
            manager.removeScoreboard(name);
          }
        }
      }
    } catch (RuntimeException ignored) {
    }
    tabScoreboardNames.clear();
  }

  private void removeLobbyScoreboard(UUID playerId) {
    String name = tabScoreboardNames.remove(playerId);
    if (name == null) {
      return;
    }
    try {
      me.neznamy.tab.api.scoreboard.ScoreboardManager manager =
          TabAPI.getInstance().getScoreboardManager();
      if (manager != null && manager.getRegisteredScoreboards().containsKey(name)) {
        manager.removeScoreboard(name);
      }
    } catch (RuntimeException ignored) {
    }
  }

  private void refreshStats(Player player) {
    if (stats == null || !stats.isAvailable()) {
      return;
    }
    UUID playerId = player.getUniqueId();
    String playerName = player.getName();
    long now = System.currentTimeMillis();
    long refreshedAt = statsRefreshAt.getOrDefault(playerId, 0L);
    if (now - refreshedAt < STATS_REFRESH_INTERVAL_MILLIS
        || !pendingStatsRequests.add(playerId)) {
      return;
    }
    stats.playerStats(playerId).whenComplete((storedStats, exception) -> {
      pendingStatsRequests.remove(playerId);
      if (exception != null) {
        plugin.getLogger().fine("Failed to load lobby stats for " + playerName
            + ": " + exception.getMessage());
        return;
      }
      statsRefreshAt.put(playerId, System.currentTimeMillis());
      storedStats.ifPresentOrElse(
          value -> statsCache.put(playerId, value),
          () -> statsCache.put(playerId, new StoredPlayerStats(playerId, playerName, 0, 0, 0)));
      Bukkit.getScheduler().runTask(plugin, () -> {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null && isLobbyPlayer(online)) {
          applyLobbyScoreboard(online);
        }
      });
    });
  }

  private String render(Player player, String text) {
    StoredPlayerStats playerStats = statsCache.getOrDefault(player.getUniqueId(),
        new StoredPlayerStats(player.getUniqueId(), player.getName(), 0, 0, 0));
    return text.replace("{player}", player.getName())
        .replace("{world}", player.getWorld().getName())
        .replace("{online}", Integer.toString(Bukkit.getOnlinePlayers().size()))
        .replace("{kills}", Integer.toString(playerStats.kills()))
        .replace("{wins}", Integer.toString(playerStats.wins()))
        .replace("{experience}", Integer.toString(playerStats.experience()))
        .replace("{exp}", Integer.toString(playerStats.experience()))
        .replace("{points}", Integer.toString(playerStats.experience()))
        .replace("{score}", Integer.toString(playerStats.experience()));
  }

  private String color(String text) {
    return text.replace('&', '§');
  }
}
