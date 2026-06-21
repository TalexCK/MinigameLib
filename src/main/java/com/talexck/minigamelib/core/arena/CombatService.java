package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaStopReason;
import com.talexck.minigamelib.api.arena.ArenaTeam;
import com.talexck.minigamelib.api.arena.ArenaTeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;
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

  private final ArenaRegistry registry;
  private final ArenaDisplay display;
  private final ArenaLifecycleControl lifecycle;
  private final ConcurrentMap<UUID, DeathCredit> deathCredits = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, String> creeperOwners = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, RecentCreeperPlacement> recentCreeperPlacements =
      new ConcurrentHashMap<>();

  CombatService(JavaPlugin plugin, ArenaRegistry registry, ArenaDisplay display,
      ArenaLifecycleControl lifecycle) {
    this.registry = registry;
    this.display = display;
    this.lifecycle = lifecycle;
    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();
    RuntimeArena arena = registry.findRunningByPlayer(player.getName()).orElse(null);
    if (arena == null) {
      return;
    }
    event.setShowDeathMessages(false);
    event.deathMessage(null);

    Player directKiller = player.getKiller();
    String creditedKillerName = null;
    String messageKillerName = null;
    if (directKiller != null && arena.playerNames().contains(directKiller.getName())) {
      messageKillerName = directKiller.getName();
      if (isCreditableKill(arena, player.getName(), directKiller.getName())) {
        creditedKillerName = directKiller.getName();
        arena.recordKill(creditedKillerName);
        arena.listener().onKillPlayer(arena.handle(), creditedKillerName, player.getName());
      }
    }
    DeathCredit credit = validDeathCredit(player).orElse(null);
    if (messageKillerName == null && credit != null && credit.killerName() != null
        && arena.playerNames().contains(credit.killerName())) {
      messageKillerName = credit.killerName();
      if (isCreditableKill(arena, player.getName(), credit.killerName())) {
        creditedKillerName = credit.killerName();
        arena.recordKill(creditedKillerName);
        arena.listener().onKillPlayer(arena.handle(), creditedKillerName, player.getName());
      }
    }
    broadcastDeathMessage(arena, player.getName(), messageKillerName, credit);
    failPlayer(arena, player, creditedKillerName);
  }

  @EventHandler
  public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player victim)) {
      return;
    }
    RuntimeArena arena = registry.findByPlayer(victim.getName()).orElse(null);
    if (arena == null) {
      return;
    }
    if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
        || event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
        || event.getDamager() instanceof TNTPrimed || event.getDamager() instanceof Creeper) {
      recordDamageCredit(arena, victim, event.getDamager());
      return;
    }
    Player attacker = attackingPlayer(event.getDamager());
    if (attacker == null || attacker.equals(victim)) {
      return;
    }
    Optional<ArenaTeamColor> attackerTeam = arena.teamOf(attacker.getName());
    Optional<ArenaTeamColor> victimTeam = arena.teamOf(victim.getName());
    if (attackerTeam.isPresent() && attackerTeam.equals(victimTeam)) {
      event.setCancelled(true);
      deathCredits.remove(victim.getUniqueId());
      return;
    }
    recordDamageCredit(arena, victim, event.getDamager());
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
      deathCredits.put(victim.getUniqueId(), new DeathCredit(killerName, DeathSource.CREEPER, "苦力怕",
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
        itemName == null || itemName.isBlank() ? "药水球" : itemName,
        System.currentTimeMillis() + POTION_CREDIT_TTL_MILLIS));
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
        result = result.append(renderedKiller == null ? Component.text("未知来源")
            : coloredPlayerName(arena, renderedKiller));
        index += "{killer}".length();
      } else if (template.startsWith("{source}", index)) {
        result = result
            .append(Component.text(credit == null ? "" : credit.sourceName(), NamedTextColor.GOLD));
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
      arena.listener().onPlayerFailed(arena.handle(), player.getName(), teamColor);
    }
    if (teamColor != null && !teamWasFailed && arena.isTeamFailed(teamColor)) {
      List<String> failedTeamPlayers =
          arena.teams().stream().filter(team -> team.color() == teamColor).findFirst()
              .map(ArenaTeam::playerNames).orElse(List.of());
      arena.listener().onTeamFailed(arena.handle(), teamColor, failedTeamPlayers);
    }
    display.refreshScoreboards(arena, 0);
    checkVictory(arena);
  }

  private void checkVictory(RuntimeArena arena) {
    if (arena.settings().victoryCondition() == null) {
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
