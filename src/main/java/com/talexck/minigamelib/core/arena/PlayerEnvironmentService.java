package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.arena.ArenaSettings;
import com.talexck.minigamelib.api.arena.ArenaTeam;
import com.talexck.minigamelib.api.arena.ArenaTeamColor;
import com.talexck.minigamelib.core.resourcepack.ResourcePackService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Owns player environment concerns: lobby/arena gamemode on join, teleporting teams to spawn and
 * back, sending available resource packs, suppressing advancement toasts, and lobby tablist reset
 * on join/quit.
 */
final class PlayerEnvironmentService implements Listener {

  private final JavaPlugin plugin;
  private final SpawnCageService spawnCageService;
  private final DisplayService display;
  private final TabDisplayService tab;
  private final ResourcePackService resourcePackService;
  private final Supplier<List<ArenaSettings>> knownSettings;

  PlayerEnvironmentService(JavaPlugin plugin, SpawnCageService spawnCageService,
      DisplayService display, TabDisplayService tab,
      ResourcePackService resourcePackService, Supplier<List<ArenaSettings>> knownSettings) {
    this.plugin = plugin;
    this.spawnCageService = spawnCageService;
    this.display = display;
    this.tab = tab;
    this.resourcePackService = resourcePackService;
    this.knownSettings = knownSettings;
    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  void teleportPlayersToSpawn(RuntimeArena arena) {
    World world = arena.world().world();
    Map<ArenaTeamColor, List<ArenaPoint>> teamSpawns =
        TeamDistribution.teamSpawnMap(arena.layout().teamSpawns());
    for (ArenaTeam team : arena.teams()) {
      List<ArenaPoint> spawnPoints =
          teamSpawns.getOrDefault(team.color(), arena.layout().spawnPoints());
      spawnCageService.createTeamSpawnCage(arena.arenaId(), world, spawnPoints);
      for (int index = 0; index < team.playerNames().size(); index++) {
        Player player = Bukkit.getPlayerExact(team.playerNames().get(index));
        if (player == null || spawnPoints.isEmpty()) {
          continue;
        }
        ArenaPoint point = spawnPoints.get(index % spawnPoints.size());
        player.teleport(safeSpawnLocation(point.toLocation(world)));
        player.setGameMode(GameMode.ADVENTURE);
        display.applyPlayerListName(arena, player);
      }
    }
  }

  private Location safeSpawnLocation(Location location) {
    Location adjusted = location.clone();
    for (int attempt = 0; attempt < 4; attempt++) {
      if (adjusted.getBlock().isPassable()
          && adjusted.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) {
        return adjusted;
      }
      adjusted.add(0.0, 1.0, 0.0);
    }
    return adjusted;
  }

  void teleportPlayersBack(RuntimeArena arena) {
    World returnWorld = Bukkit.getWorld(arena.settings().returnWorldName());
    if (returnWorld == null) {
      throw new IllegalStateException(
          "Return world not found: " + arena.settings().returnWorldName());
    }
    Location returnLocation = arena.settings().returnPoint().toLocation(returnWorld);
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        player.teleport(returnLocation);
        player.setGameMode(GameMode.ADVENTURE);
        player.playerListName(Component.text(player.getName()));
      }
    }
    tab.resetLobbyTabViews();
  }

  void setArenaPlayersGameMode(RuntimeArena arena, GameMode gameMode) {
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        player.setGameMode(gameMode);
      }
    }
  }

  @EventHandler
  public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
    event.message(null);
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    Bukkit.getScheduler().runTask(plugin, () -> {
      event.getPlayer().setGameMode(GameMode.ADVENTURE);
      tab.resetLobbyTabViews();
      sendAvailableResourcePacks(event.getPlayer());
    });
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    resourcePackService.clearPlayer(event.getPlayer().getUniqueId());
    Bukkit.getScheduler().runTask(plugin, tab::resetLobbyTabViews);
  }

  private void sendAvailableResourcePacks(Player player) {
    for (ArenaSettings setting : knownSettings.get()) {
      if (setting.resourcePack().enabled()) {
        resourcePackService.sendResourcePack(player, setting.resourcePack());
      }
    }
  }

  void shutdown() {
    HandlerList.unregisterAll(this);
  }
}
