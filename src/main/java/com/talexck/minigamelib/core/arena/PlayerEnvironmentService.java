package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.arena.ArenaSettings;
import com.talexck.minigamelib.api.arena.ArenaTeam;
import com.talexck.minigamelib.api.arena.ArenaTeamColor;
import com.talexck.minigamelib.core.resourcepack.ResourcePackService;
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
  private final ArenaRegistry registry;
  private java.util.function.Consumer<Player> returnHandler = player -> {
  };

  PlayerEnvironmentService(JavaPlugin plugin, ArenaRegistry registry,
      SpawnCageService spawnCageService, DisplayService display, TabDisplayService tab,
      ResourcePackService resourcePackService, Supplier<List<ArenaSettings>> knownSettings) {
    this.plugin = plugin;
    this.registry = registry;
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
        restorePlayer(player);
        Location spawn = safeSpawnLocation(point.toLocation(world));
        org.bukkit.util.Vector towardsCenter = arena.layout().center().toLocation(world).toVector()
            .subtract(spawn.toVector()).setY(0);
        if (towardsCenter.lengthSquared() > 0.01) {
          spawn.setDirection(towardsCenter.normalize());
        }
        player.teleport(spawn);
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
      returnWorld = Bukkit.getWorlds().getFirst();
      plugin.getLogger().warning("Return world not found: " + arena.settings().returnWorldName()
          + ", using " + returnWorld.getName());
    }
    Location returnLocation = arena.settings().returnPoint().toLocation(returnWorld);
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null && player.getWorld().equals(arena.world().world())) {
        restorePlayer(player);
        player.teleport(returnLocation);
        player.setGameMode(GameMode.ADVENTURE);
        player.playerListName(null);
        returnHandler.accept(player);
      }
    }
    tab.scheduleLobbyRefresh();
  }

  /** Moves anyone still in the arena world (e.g. staff spectating) out so it can unload. */
  void evacuateWorld(RuntimeArena arena) {
    World returnWorld = Bukkit.getWorld(arena.settings().returnWorldName());
    if (returnWorld == null) {
      returnWorld = Bukkit.getWorlds().getFirst();
    }
    Location returnLocation = arena.settings().returnPoint().toLocation(returnWorld);
    for (Player player : List.copyOf(arena.world().world().getPlayers())) {
      player.teleport(returnLocation);
      if (player.getGameMode() == GameMode.SPECTATOR) {
        player.setGameMode(GameMode.ADVENTURE);
      }
    }
  }

  /** Resets health, hunger, effects and inventory so nothing leaks from a game into the lobby. */
  void restorePlayer(Player player) {
    player.getInventory().clear();
    player.getInventory().setArmorContents(null);
    player.getInventory().setItemInOffHand(null);
    player.setItemOnCursor(null);
    for (org.bukkit.potion.PotionEffect effect : List.copyOf(player.getActivePotionEffects())) {
      player.removePotionEffect(effect.getType());
    }
    org.bukkit.attribute.AttributeInstance maxHealth =
        player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
    player.setHealth(maxHealth == null ? 20.0 : maxHealth.getValue());
    player.setFoodLevel(20);
    player.setSaturation(20.0f);
    player.setFireTicks(0);
    player.setFallDistance(0.0f);
    player.setLevel(0);
    player.setExp(0.0f);
  }

  /** No projectiles, orbs or item use while players are caged during the countdown. */
  @EventHandler(ignoreCancelled = false)
  public void onCountdownInteract(org.bukkit.event.player.PlayerInteractEvent event) {
    RuntimeArena arena = registry.findByPlayer(event.getPlayer().getName()).orElse(null);
    if (arena != null && arena.status() == com.talexck.minigamelib.api.arena.ArenaStatus.COUNTDOWN
        && event.getAction() != org.bukkit.event.block.Action.PHYSICAL) {
      event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onCountdownDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
    RuntimeArena arena = registry.findByPlayer(event.getPlayer().getName()).orElse(null);
    if (arena != null && arena.status() == com.talexck.minigamelib.api.arena.ArenaStatus.COUNTDOWN) {
      event.setCancelled(true);
    }
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

  /**
   * A player rejoining while their game still runs comes back as a spectator of that game;
   * everybody else is prepared for the lobby.
   */
  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    Bukkit.getScheduler().runTask(plugin, () -> {
      if (!player.isOnline()) {
        return;
      }
      RuntimeArena arena = registry.findByPlayer(player.getName()).orElse(null);
      if (arena != null) {
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        Location spectate = arena.layout().center().toLocation(arena.world().world())
            .add(0.5, 12.0, 0.5);
        spectate.setPitch(35.0f);
        player.teleport(spectate);
        display.applyPlayerListName(arena, player);
        display.refreshScoreboards(arena, 0);
      } else {
        if (arenaWorldNames().contains(player.getWorld().getName())
            || player.getGameMode() == GameMode.SPECTATOR) {
          restorePlayer(player);
        }
        player.setGameMode(GameMode.ADVENTURE);
      }
      tab.scheduleLobbyRefresh();
      sendAvailableResourcePacks(player);
    });
  }

  private java.util.Set<String> arenaWorldNames() {
    java.util.Set<String> names = new java.util.HashSet<>();
    registry.stream().forEach(arena -> names.add(arena.world().runtimeWorldName()));
    return names;
  }

  /** Whether the player belongs to an arena that is counting down or running. */
  boolean isPlaying(Player player) {
    return registry.findByPlayer(player.getName()).isPresent();
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    resourcePackService.clearPlayer(event.getPlayer().getUniqueId());
    tab.scheduleLobbyRefresh();
  }

  private void sendAvailableResourcePacks(Player player) {
    for (ArenaSettings setting : knownSettings.get()) {
      if (setting.resourcePack().enabled()) {
        try {
          resourcePackService.sendResourcePack(player, setting.resourcePack());
        } catch (RuntimeException exception) {
          plugin.getLogger().warning("Resource pack delivery failed: " + exception.getMessage());
        }
      }
    }
  }

  /** Called for each player sent back to the return point (e.g. to hand out lobby items). */
  void setReturnHandler(java.util.function.Consumer<Player> returnHandler) {
    this.returnHandler = returnHandler == null ? player -> {
    } : returnHandler;
  }

  void shutdown() {
    HandlerList.unregisterAll(this);
  }
}
