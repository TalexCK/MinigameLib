package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaPresentation;
import com.talexck.minigamelib.api.arena.ArenaStatus;
import com.talexck.minigamelib.api.arena.ArenaStopReason;
import com.talexck.minigamelib.api.arena.ArenaTeam;
import com.talexck.minigamelib.api.arena.ArenaTeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Handles combat outcomes: kill crediting (direct, TNT, creeper, potion), death broadcast messages,
 * player/team elimination, and victory detection. Owns all death-credit and creeper-ownership state.
 */
final class CombatService implements Listener {

  private static final long DAMAGE_CREDIT_TTL_MILLIS = 10_000L;
  static final long POTION_CREDIT_TTL_MILLIS = 12_000L;

  private final JavaPlugin plugin;
  private final ArenaRegistry registry;
  private final DisplayService display;
  private final ArenaLifecycleControl lifecycle;
  private final ConcurrentMap<UUID, DeathCredit> deathCredits = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, String> creeperOwners = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, RecentCreeperPlacement> recentCreeperPlacements =
      new ConcurrentHashMap<>();

  CombatService(JavaPlugin plugin, ArenaRegistry registry, DisplayService display,
      ArenaLifecycleControl lifecycle) {
    this.plugin = plugin;
    this.registry = registry;
    this.display = display;
    this.lifecycle = lifecycle;
    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onEntityDamage(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof Player player)) {
      return;
    }
    RuntimeArena arena = registry.findByPlayer(player.getName()).orElse(null);
    if (arena == null) {
      return;
    }
    if (arena.status() != ArenaStatus.RUNNING || arena.isFailed(player.getName())) {
      // Nobody can be hurt while caged in the countdown, and spectators are out of the game.
      event.setCancelled(true);
      return;
    }
    if (event.getFinalDamage() < player.getHealth()
        && event.getCause() != EntityDamageEvent.DamageCause.VOID) {
      return;
    }
    if (event instanceof EntityDamageByEntityEvent entityDamage) {
      recordDamageCredit(arena, player, entityDamage.getDamager());
    }
    event.setCancelled(true);
    eliminatePlayer(arena, player, false);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player victim)) {
      return;
    }
    RuntimeArena arena = registry.findByPlayer(victim.getName()).orElse(null);
    if (arena == null) {
      return;
    }
    if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
        || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
        || event.getDamager() instanceof TNTPrimed || event.getDamager() instanceof Creeper) {
      recordDamageCredit(arena, victim, event.getDamager());
      return;
    }
    Player attacker = attackingPlayer(event.getDamager());
    if (attacker == null || attacker.equals(victim)) {
      return;
    }
    if (arena.isFailed(attacker.getName())) {
      event.setCancelled(true);
      return;
    }
    Optional<ArenaTeamColor> attackerTeam = arena.teamOf(attacker.getName());
    Optional<ArenaTeamColor> victimTeam = arena.teamOf(victim.getName());
    if (attackerTeam.isPresent() && attackerTeam.equals(victimTeam)) {
      event.setCancelled(true);
      return;
    }
    recordDamageCredit(arena, victim, event.getDamager());
  }

  /**
   * A player leaving mid-game is eliminated (credited to their last attacker, like a combat log).
   * During the countdown they simply forfeit their slot.
   */
  @EventHandler(priority = EventPriority.LOWEST)
  public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    RuntimeArena arena = registry.findByPlayer(player.getName()).orElse(null);
    if (arena == null || arena.isFailed(player.getName())) {
      return;
    }
    if (arena.status() == ArenaStatus.RUNNING) {
      eliminatePlayer(arena, player, true);
    } else {
      ArenaTeamColor teamColor = arena.teamOf(player.getName()).orElse(null);
      boolean teamWasFailed = teamColor != null && arena.isTeamFailed(teamColor);
      arena.markFailedWithoutDeath(player.getName());
      arena.listener().onPlayerFailed(arena.handle(), player.getName(), teamColor);
      if (teamColor != null && !teamWasFailed && arena.isTeamFailed(teamColor)) {
        arena.listener().onTeamFailed(arena.handle(), teamColor, teamPlayers(arena, teamColor));
      }
      display.refreshScoreboards(arena, 0);
    }
    player.getInventory().clear();
    player.getInventory().setArmorContents(null);
    player.getInventory().setItemInOffHand(null);
  }

  private boolean isCreditableKill(RuntimeArena arena, String victimName, String killerName) {
    if (killerName == null || victimName == null || victimName.equals(killerName)
        || !arena.playerNames().contains(killerName)) {
      return false;
    }
    Optional<ArenaTeamColor> victimTeam = arena.teamOf(victimName);
    Optional<ArenaTeamColor> killerTeam = arena.teamOf(killerName);
    return victimTeam.isEmpty() || killerTeam.isEmpty() || !victimTeam.equals(killerTeam);
  }

  @EventHandler
  public void onEntityPlace(EntityPlaceEvent event) {
    if (!(event.getEntity() instanceof Creeper) || event.getPlayer() == null) {
      return;
    }
    RuntimeArena arena = registry.findByPlayer(event.getPlayer().getName()).orElse(null);
    if (arena != null) {
      creeperOwners.put(event.getEntity().getUniqueId(), event.getPlayer().getName());
    }
  }

  @EventHandler
  public void onCreatureSpawn(CreatureSpawnEvent event) {
    if (!(event.getEntity() instanceof Creeper creeper)
        || event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
      return;
    }
    findRecentCreeperOwner(event.getLocation()).ifPresent(owner -> {
      if (registry.findByPlayer(owner).isPresent()) {
        creeperOwners.put(creeper.getUniqueId(), owner);
      }
    });
  }

  private void recordDamageCredit(RuntimeArena arena, Player victim, Entity damager) {
    if (damager instanceof TNTPrimed tnt) {
      String killerName = tnt.getSource() instanceof Player player ? player.getName() : null;
      deathCredits.put(victim.getUniqueId(), new DeathCredit(killerName, DeathSource.TNT, "TNT",
          System.currentTimeMillis() + DAMAGE_CREDIT_TTL_MILLIS));
      return;
    }
    if (damager instanceof Creeper creeper) {
      String killerName = creeperOwners.get(creeper.getUniqueId());
      deathCredits.put(victim.getUniqueId(), new DeathCredit(killerName, DeathSource.CREEPER,
          display.text("combat.source-creeper"),
          System.currentTimeMillis() + DAMAGE_CREDIT_TTL_MILLIS));
      return;
    }
    Player attacker = attackingPlayer(damager);
    if (attacker != null && arena.playerNames().contains(attacker.getName())) {
      deathCredits.put(victim.getUniqueId(), new DeathCredit(attacker.getName(), DeathSource.PLAYER,
          "", System.currentTimeMillis() + DAMAGE_CREDIT_TTL_MILLIS));
    }
  }

  /** Records a potion-sphere kill credit (called by the projectile/potion subsystem). */
  void creditPotionDeath(UUID victimId, String shooterName, String itemName) {
    deathCredits.put(victimId, new DeathCredit(shooterName, DeathSource.POTION,
        itemName == null || itemName.isBlank() ? display.text("combat.source-potion") : itemName,
        System.currentTimeMillis() + POTION_CREDIT_TTL_MILLIS));
  }

  void eliminatePlayer(Player player) {
    RuntimeArena arena = registry.findRunningByPlayer(player.getName()).orElse(null);
    if (arena == null || arena.isFailed(player.getName())) {
      return;
    }
    eliminatePlayer(arena, player, false);
  }

  private void eliminatePlayer(RuntimeArena arena, Player player, boolean disconnected) {
    DeathCredit credit = validDeathCredit(player).orElse(null);
    String messageKillerName = credit == null ? null : credit.killerName();
    String creditedKillerName = creditableKillerName(arena, player.getName(), messageKillerName);
    int killScore = arena.settings().rules().killScore();
    if (creditedKillerName != null) {
      arena.recordKill(creditedKillerName);
      arena.addScore(creditedKillerName, killScore);
      arena.listener().onKillPlayer(arena.handle(), creditedKillerName, player.getName());
    }
    Location deathLocation = player.getLocation();
    dropInventoryExceptBlocks(player);
    if (!disconnected) {
      fakeRespawn(arena, player, deathLocation);
    }
    broadcastDeathMessage(arena, player.getName(), messageKillerName, credit);
    failPlayer(arena, player, creditedKillerName);
    sendEliminationFeedback(arena, player, disconnected, messageKillerName, creditedKillerName,
        killScore);
    checkVictory(arena);
  }

  private void sendEliminationFeedback(RuntimeArena arena, Player victim, boolean disconnected,
      String killerName, String creditedKillerName, int killScore) {
    ArenaPresentation presentation = arena.settings().presentation();
    Map<String, String> extra = Map.of("{victim}", victim.getName(),
        "{killer}", killerName == null ? display.text("combat.unknown-killer") : killerName,
        "{gained}", Integer.toString(killScore));
    if (!disconnected) {
      display.showTitle(victim, arena, presentation.eliminatedTitle(),
          presentation.eliminatedSubtitle(), extra, Duration.ofMillis(100),
          Duration.ofMillis(2200), Duration.ofMillis(500));
      victim.playSound(victim.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.6f);
    }
    if (creditedKillerName != null) {
      Player killer = Bukkit.getPlayerExact(creditedKillerName);
      if (killer != null) {
        display.showActionBar(killer, arena, presentation.killActionBar(), extra);
        killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        killer.playSound(killer.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 2.0f);
      }
    }
    for (String playerName : arena.playerNames()) {
      Player viewer = Bukkit.getPlayerExact(playerName);
      if (viewer != null && !viewer.equals(victim)
          && !playerName.equals(creditedKillerName)) {
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 0.4f,
            0.7f);
      }
    }
  }

  private String creditableKillerName(RuntimeArena arena, String victimName, String killerName) {
    if (killerName == null || !arena.playerNames().contains(killerName)) {
      return null;
    }
    return isCreditableKill(arena, victimName, killerName) ? killerName : null;
  }

  private void dropInventoryExceptBlocks(Player player) {
    Location location = player.getLocation();
    World world = location.getWorld();
    if (world == null) {
      player.getInventory().clear();
      return;
    }
    for (ItemStack stack : player.getInventory().getContents()) {
      dropIfNonBlock(world, location, stack);
    }
    player.getInventory().clear();
    player.getInventory().setArmorContents(null);
    player.getInventory().setItemInOffHand(null);
  }

  private void dropIfNonBlock(World world, Location location, ItemStack stack) {
    if (stack == null || stack.getType() == Material.AIR || stack.getType().isBlock()) {
      return;
    }
    world.dropItemNaturally(location, stack.clone());
  }

  private void fakeRespawn(RuntimeArena arena, Player player, Location deathLocation) {
    player.setFireTicks(0);
    player.setFallDistance(0.0f);
    player.setHealth(Math.max(1.0, maxHealth(player)));
    player.setFoodLevel(20);
    player.setSaturation(20.0f);
    for (org.bukkit.potion.PotionEffect effect : List.copyOf(player.getActivePotionEffects())) {
      player.removePotionEffect(effect.getType());
    }
    player.setGameMode(GameMode.SPECTATOR);
    World world = arena.world().world();
    if (!world.equals(deathLocation.getWorld())
        || deathLocation.getY() < world.getMinHeight() + 2) {
      // Fell into the void: park the spectator above the arena center instead of under the map.
      Location spectate = arena.layout().center().toLocation(world).add(0.5, 12.0, 0.5);
      spectate.setYaw(deathLocation.getYaw());
      spectate.setPitch(35.0f);
      player.teleport(spectate);
    }
  }

  private double maxHealth(Player player) {
    AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
    return attribute == null ? 20.0 : attribute.getValue();
  }

  private Optional<DeathCredit> validDeathCredit(Player player) {
    DeathCredit credit = deathCredits.remove(player.getUniqueId());
    if (credit == null || credit.expiresAtMillis() < System.currentTimeMillis()) {
      return Optional.empty();
    }
    return Optional.of(credit);
  }

  private void broadcastDeathMessage(RuntimeArena arena, String victimName, String killerName,
      DeathCredit credit) {
    String template = deathTemplate(arena, killerName, credit);
    for (String playerName : arena.playerNames()) {
      Player viewer = Bukkit.getPlayerExact(playerName);
      if (viewer != null) {
        viewer.sendMessage(renderDeathComponent(arena, template, victimName, killerName, credit));
      }
    }
  }

  private String deathTemplate(RuntimeArena arena, String killerName, DeathCredit credit) {
    if (credit == null) {
      return killerName == null ? arena.settings().messages().deathGeneric()
          : arena.settings().messages().deathByPlayer();
    }
    return switch (credit.source()) {
      case TNT -> arena.settings().messages().deathByTnt();
      case CREEPER -> arena.settings().messages().deathByCreeper();
      case POTION -> arena.settings().messages().deathByPotion();
      case PLAYER -> arena.settings().messages().deathByPlayer();
    };
  }

  private Component renderDeathComponent(RuntimeArena arena, String template, String victimName,
      String killerName, DeathCredit credit) {
    Component result = Component.empty();
    int index = 0;
    while (index < template.length()) {
      if (template.startsWith("{victim}", index)) {
        result = result.append(coloredPlayerName(arena, victimName));
        index += "{victim}".length();
      } else if (template.startsWith("{killer}", index)) {
        String renderedKiller =
            killerName == null && credit != null ? credit.killerName() : killerName;
        result = result.append(renderedKiller == null
            ? LegacyText.component(display.text("combat.unknown-killer"))
            : coloredPlayerName(arena, renderedKiller));
        index += "{killer}".length();
      } else if (template.startsWith("{source}", index)) {
        result = result.append(LegacyText.component(credit == null ? "" : credit.sourceName())
            .colorIfAbsent(NamedTextColor.GOLD));
        index += "{source}".length();
      } else {
        int next = nextDeathPlaceholderIndex(template, index);
        result = result.append(LegacyText.component(template.substring(index, next)));
        index = next;
      }
    }
    return result;
  }

  private int nextDeathPlaceholderIndex(String template, int start) {
    int next = template.length();
    for (String placeholder : List.of("{victim}", "{killer}", "{source}")) {
      int index = template.indexOf(placeholder, start);
      if (index >= 0) {
        next = Math.min(next, index);
      }
    }
    return next;
  }

  private Component coloredPlayerName(RuntimeArena arena, String playerName) {
    NamedTextColor color =
        arena.teamOf(playerName).map(TeamPalette::textColor).orElse(NamedTextColor.WHITE);
    return Component.text(playerName, color);
  }

  private void failPlayer(RuntimeArena arena, Player player, String killerName) {
    ArenaTeamColor teamColor = arena.teamOf(player.getName()).orElse(null);
    boolean wasFailed = arena.isFailed(player.getName());
    boolean teamWasFailed = teamColor != null && arena.isTeamFailed(teamColor);
    arena.recordDeath(player.getName());
    if (killerName != null) {
      arena.listener().onPlayerKilled(arena.handle(), player.getName(), killerName);
    }
    if (!wasFailed && arena.isFailed(player.getName())) {
      awardOutliveScore(arena, player.getName(), teamColor);
      arena.listener().onPlayerFailed(arena.handle(), player.getName(), teamColor);
    }
    if (teamColor != null && !teamWasFailed && arena.isTeamFailed(teamColor)) {
      arena.listener().onTeamFailed(arena.handle(), teamColor, teamPlayers(arena, teamColor));
      broadcastTeamEliminated(arena, teamColor);
    }
    display.refreshScoreboards(arena, 0);
  }

  /** Every surviving enemy of the eliminated player earns the configured outlive score. */
  private void awardOutliveScore(RuntimeArena arena, String victimName,
      ArenaTeamColor victimTeam) {
    int outliveScore = arena.settings().rules().outliveScore();
    if (outliveScore <= 0) {
      return;
    }
    for (String playerName : arena.playerNames()) {
      if (playerName.equals(victimName) || arena.isFailed(playerName)) {
        continue;
      }
      if (victimTeam != null && arena.teamOf(playerName).map(victimTeam::equals).orElse(false)) {
        continue;
      }
      arena.addScore(playerName, outliveScore);
    }
  }

  private void broadcastTeamEliminated(RuntimeArena arena, ArenaTeamColor teamColor) {
    String template = arena.settings().presentation().teamEliminatedMessage();
    if (template.isBlank() || arena.teams().stream().allMatch(
        team -> team.playerNames().size() <= 1)) {
      // In solo modes the death message already says everything.
      return;
    }
    String teamName = TeamPalette.legacyCode(teamColor) + TeamPalette.displayName(teamColor);
    for (String playerName : arena.playerNames()) {
      Player viewer = Bukkit.getPlayerExact(playerName);
      if (viewer != null) {
        viewer.sendMessage(LegacyText.component(display.render(arena, template, 0, null,
            playerName).replace("{team}", teamName)));
      }
    }
  }

  private List<String> teamPlayers(RuntimeArena arena, ArenaTeamColor teamColor) {
    return arena.teams().stream().filter(team -> team.color() == teamColor).findFirst()
        .map(ArenaTeam::playerNames).orElse(List.of());
  }

  void checkVictory(RuntimeArena arena) {
    if (arena.settings().victoryCondition() == null || arena.status() != ArenaStatus.RUNNING) {
      return;
    }
    if (arena.aliveTeamCount() == 0) {
      lifecycle.stop(arena.arenaId(), ArenaStopReason.NORMAL);
      return;
    }
    arena.singleAliveTeam().ifPresent(winner -> {
      arena.setWinningTeam(winner);
      lifecycle.stop(arena.arenaId(), ArenaStopReason.NORMAL);
    });
  }

  /** Remembers where a player threw a creeper spawn egg, to credit creeper kills shortly after. */
  void rememberCreeperPlacement(Player player, Location location) {
    recentCreeperPlacements.put(player.getUniqueId(),
        new RecentCreeperPlacement(player.getName(), location, System.currentTimeMillis()));
  }

  private Optional<String> findRecentCreeperOwner(Location spawnLocation) {
    long now = System.currentTimeMillis();
    recentCreeperPlacements.entrySet().removeIf(entry -> now - entry.getValue().createdAtMillis()
        > 3_000L);
    return recentCreeperPlacements.values().stream()
        .filter(placement -> placement.location().getWorld() != null
            && placement.location().getWorld().equals(spawnLocation.getWorld()))
        .filter(placement -> placement.location().distanceSquared(spawnLocation) <= 16.0)
        .min(Comparator.comparingDouble(
            placement -> placement.location().distanceSquared(spawnLocation)))
        .map(RecentCreeperPlacement::playerName);
  }

  private Player attackingPlayer(Entity damager) {
    if (damager instanceof Player player) {
      return player;
    }
    if (damager instanceof Projectile projectile
        && projectile.getShooter() instanceof Player player) {
      return player;
    }
    return null;
  }

  void shutdown() {
    HandlerList.unregisterAll(this);
    deathCredits.clear();
    creeperOwners.clear();
    recentCreeperPlacements.clear();
  }

  private record DeathCredit(String killerName, DeathSource source, String sourceName,
      long expiresAtMillis) {
  }

  private record RecentCreeperPlacement(String playerName, Location location,
      long createdAtMillis) {
  }

  private enum DeathSource {
    PLAYER, TNT, CREEPER, POTION
  }
}
