package com.talexck.minigamelib.core.lobby;

import com.talexck.minigamelib.api.lobby.LobbyService;
import com.talexck.minigamelib.api.lobby.LobbySettings;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultLobbyService implements LobbyService, Listener {

  private final JavaPlugin plugin;
  private LobbySettings settings;
  private BukkitTask hungerTask;
  private final Map<UUID, String> tabScoreboardNames = new ConcurrentHashMap<>();

  public DefaultLobbyService(JavaPlugin plugin) {
    this.plugin = plugin;
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
    removeLobbyScoreboard(event.getPlayer().getUniqueId());
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

  private String render(Player player, String text) {
    return text.replace("{player}", player.getName())
        .replace("{world}", player.getWorld().getName())
        .replace("{online}", Integer.toString(Bukkit.getOnlinePlayers().size()));
  }

  private String color(String text) {
    return text.replace('&', '§');
  }
}
