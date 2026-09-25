package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaItemEntry;
import com.talexck.minigamelib.api.arena.ArenaItemMode;
import com.talexck.minigamelib.api.arena.ArenaPotionItemConfig;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import com.talexck.minigamelib.api.arena.ArenaStatus;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Handles thrown/self potion items: launching potion projectiles, the lingering effect sphere,
 * particles, and creeper-egg placement tracking. Depends on {@link ItemService} for item matching
 * and {@link CombatService} for kill crediting.
 */
final class ItemCombatService implements Listener {

  private final JavaPlugin plugin;
  private final ArenaRegistry registry;
  private final ItemService items;
  private final CombatService combat;
  private static final long MAX_FLIGHT_TICKS = 80L;
  private static final Set<PotionEffectType> NEGATIVE_EFFECTS = Set.of(
      PotionEffectType.POISON, PotionEffectType.INSTANT_DAMAGE, PotionEffectType.SLOWNESS,
      PotionEffectType.WEAKNESS, PotionEffectType.WITHER, PotionEffectType.BLINDNESS,
      PotionEffectType.NAUSEA, PotionEffectType.DARKNESS, PotionEffectType.MINING_FATIGUE,
      PotionEffectType.HUNGER, PotionEffectType.LEVITATION, PotionEffectType.GLOWING,
      PotionEffectType.UNLUCK);

  private final ConcurrentMap<UUID, ActivePotionProjectile> potionProjectiles =
      new ConcurrentHashMap<>();

  ItemCombatService(JavaPlugin plugin, ArenaRegistry registry, ItemService items,
      CombatService combat) {
    this.plugin = plugin;
    this.registry = registry;
    this.items = items;
    this.combat = combat;
    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_AIR
        && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
      return;
    }
    Player player = event.getPlayer();
    RuntimeArena arena = registry.findByPlayer(player.getName()).orElse(null);
    if (arena == null) {
      return;
    }
    ItemStack item = event.getItem();
    if (arena.status() != ArenaStatus.RUNNING || arena.isFailed(player.getName())) {
      if (items.findItemEntry(arena, item, ArenaItemMode.SELF_POTION).isPresent()
          || items.findItemEntry(arena, item, ArenaItemMode.POTION).isPresent()) {
        event.setCancelled(true);
      }
      return;
    }
    if (item != null && item.getType() == Material.CREEPER_SPAWN_EGG) {
      Location location = event.getClickedBlock() == null ? player.getLocation()
          : event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
      combat.rememberCreeperPlacement(player, location);
    }
    ArenaItemEntry selfPotion =
        items.findItemEntry(arena, item, ArenaItemMode.SELF_POTION).orElse(null);
    if (selfPotion != null) {
      event.setCancelled(true);
      applySelfPotion(player, selfPotion);
      items.consumeOne(item);
      return;
    }

    ArenaItemEntry entry = items.findItemEntry(arena, item, ArenaItemMode.POTION).orElse(null);
    if (entry == null) {
      return;
    }
    event.setCancelled(true);
    launchPotionFireball(arena, player, entry);
    items.consumeOne(item);
  }

  private void launchPotionFireball(RuntimeArena arena, Player player, ArenaItemEntry entry) {
    ArenaPotionItemConfig config = entry.potionConfig();
    ItemStack displayStack = projectileDisplayStack(entry);
    Snowball snowball = player.launchProjectile(Snowball.class);
    snowball.setItem(displayStack);
    snowball.setVelocity(player.getLocation().getDirection().normalize().multiply(1.5));
    snowball.setPersistent(false);
    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.8f);
    potionProjectiles.put(snowball.getUniqueId(),
        new ActivePotionProjectile(arena.arenaId(), config, player.getName(), entry.name()));
    long fuseTicks = config.fuse().isZero() ? MAX_FLIGHT_TICKS
        : Math.max(1L, Math.min(MAX_FLIGHT_TICKS, toTicks(config.fuse())));
    new BukkitRunnable() {
      private int ticks;

      @Override
      public void run() {
        RuntimeArena currentArena = registry.get(arena.arenaId());
        if (currentArena == null || snowball.isDead() || !snowball.isValid()) {
          potionProjectiles.remove(snowball.getUniqueId());
          cancel();
          return;
        }
        if (ticks >= fuseTicks) {
          explodePotionProjectile(snowball);
          cancel();
          return;
        }
        spawnPotionParticle(snowball.getLocation(), config, 2, 0.08, 0.08, 0.08);
        ticks++;
      }
    }.runTaskTimer(plugin, 0L, 1L);
  }

  @SuppressWarnings("deprecation")
  private ItemStack projectileDisplayStack(ArenaItemEntry entry) {
    ItemStack stack = entry.createStack();
    stack.setAmount(1);
    ArenaPotionItemConfig config = entry.potionConfig();
    if (config.projectileCustomModelData() > 0 || !config.itemModelKey().isBlank()) {
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
        if (config.projectileCustomModelData() > 0) {
          meta.setCustomModelData(config.projectileCustomModelData());
        }
        applyItemModel(meta, config);
        stack.setItemMeta(meta);
      }
    }
    return stack;
  }

  private void applyItemModel(ItemMeta meta, ArenaPotionItemConfig config) {
    if (config == null || config.itemModelKey().isBlank()) {
      return;
    }
    NamespacedKey key = NamespacedKey.fromString(config.itemModelKey());
    if (key != null) {
      meta.setItemModel(key);
    }
  }

  @EventHandler
  public void onProjectileHit(ProjectileHitEvent event) {
    if (event.getEntity() instanceof Snowball snowball
        && potionProjectiles.containsKey(snowball.getUniqueId())) {
      explodePotionProjectile(snowball);
    }
  }

  private void explodePotionProjectile(Snowball snowball) {
    ActivePotionProjectile projectile = potionProjectiles.remove(snowball.getUniqueId());
    if (projectile == null) {
      return;
    }
    Location location = snowball.getLocation();
    snowball.remove();
    startPotionSphere(projectile.arenaId(), location, projectile.config(), projectile.shooterName(),
        projectile.itemName());
  }

  private void applySelfPotion(Player player, ArenaItemEntry entry) {
    ArenaPotionItemConfig config = entry.potionConfig();
    applyEffect(player, config, Math.max(1, (int) toTicks(config.effectDuration())));
    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f,
        1.6f);
    player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1, 0), 20,
        0.3, 0.5, 0.3, 0.05);
    player.setCooldown(entry.item().getType(), 10);
  }

  private void applyEffect(Player player, ArenaPotionItemConfig config, int effectTicks) {
    if (config.clearNegativeEffects()) {
      for (PotionEffect effect : java.util.List.copyOf(player.getActivePotionEffects())) {
        if (NEGATIVE_EFFECTS.contains(effect.getType())) {
          player.removePotionEffect(effect.getType());
        }
      }
      player.setFireTicks(0);
    }
    if (config.effectType() != null) {
      player.addPotionEffect(new PotionEffect(config.effectType(), effectTicks,
          config.amplifier(), true, true, true));
    }
  }

  private void startPotionSphere(String arenaId, Location center, ArenaPotionItemConfig config,
      String shooterName, String itemName) {
    long durationTicks = toTicks(config.duration());
    long effectTicks = Math.max(1L, toTicks(config.effectDuration()));
    Set<String> affected = new HashSet<>();
    center.getWorld().playSound(center, Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 0.9f);
    center.getWorld().spawnParticle(Particle.FLASH, center, 1);
    new BukkitRunnable() {
      private long elapsedTicks;

      @Override
      public void run() {
        RuntimeArena arena = registry.get(arenaId);
        if (arena == null || arena.status() != ArenaStatus.RUNNING
            || elapsedTicks > durationTicks) {
          cancel();
          return;
        }
        spawnPotionSphereParticles(center, config);
        double radiusSquared = config.radius() * config.radius();
        for (String playerName : arena.playerNames()) {
          Player player = Bukkit.getPlayerExact(playerName);
          if (player == null || arena.isFailed(playerName)
              || !player.getWorld().equals(center.getWorld())
              || player.getLocation().add(0, 1, 0).distanceSquared(center) > radiusSquared) {
            continue;
          }
          if (config.affectEachPlayerOnce() && !affected.add(playerName)) {
            continue;
          }
          if (config.effectType() != null && isOffensiveEffect(config.effectType())
              && !playerName.equals(shooterName)) {
            combat.creditPotionDeath(player.getUniqueId(), shooterName, itemName);
          }
          applyEffect(player, config, (int) effectTicks);
        }
        elapsedTicks += 10L;
      }
    }.runTaskTimer(plugin, 0L, 10L);
  }

  private void spawnPotionSphereParticles(Location center, ArenaPotionItemConfig config) {
    double radius = config.radius();
    for (int index = 0; index < 42; index++) {
      double theta = 2.399963229728653 * index;
      double y = 1.0 - (2.0 * index / 41.0);
      double circleRadius = Math.sqrt(Math.max(0.0, 1.0 - y * y));
      Location point = center.clone().add(Math.cos(theta) * circleRadius * radius,
          y * radius * 0.75, Math.sin(theta) * circleRadius * radius);
      spawnPotionParticle(point, config, 1, 0.02, 0.02, 0.02);
    }
  }

  private void spawnPotionParticle(Location location, ArenaPotionItemConfig config, int count,
      double offsetX, double offsetY, double offsetZ) {
    Particle particle = particleFor(config.effectType());
    if (particle == Particle.DUST) {
      location.getWorld().spawnParticle(Particle.DUST, location, count, offsetX, offsetY, offsetZ,
          0.0, dustFor(config.effectType()));
      return;
    }
    location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, 0.01);
  }

  private Particle particleFor(PotionEffectType effectType) {
    if (effectType == PotionEffectType.REGENERATION) {
      return Particle.HEART;
    }
    if (effectType == PotionEffectType.LEVITATION) {
      return Particle.CLOUD;
    }
    return Particle.DUST;
  }

  private Particle.DustOptions dustFor(PotionEffectType effectType) {
    if (effectType == null) {
      return new Particle.DustOptions(Color.fromRGB(0xBFF4FF), 1.1f);
    }
    if (effectType == PotionEffectType.POISON) {
      return new Particle.DustOptions(Color.fromRGB(0x4E9331), 1.15f);
    }
    if (effectType == PotionEffectType.INSTANT_DAMAGE) {
      return new Particle.DustOptions(Color.fromRGB(0xA9153A), 1.2f);
    }
    if (effectType == PotionEffectType.SLOWNESS) {
      return new Particle.DustOptions(Color.fromRGB(0x5A6C81), 1.1f);
    }
    if (effectType == PotionEffectType.WEAKNESS) {
      return new Particle.DustOptions(Color.fromRGB(0x484D48), 1.1f);
    }
    if (effectType == PotionEffectType.SPEED) {
      return new Particle.DustOptions(Color.fromRGB(0x33EBFF), 1.1f);
    }
    return new Particle.DustOptions(Color.WHITE, 1.0f);
  }

  private boolean isOffensiveEffect(PotionEffectType effectType) {
    return NEGATIVE_EFFECTS.contains(effectType);
  }

  void shutdown() {
    HandlerList.unregisterAll(this);
    potionProjectiles.clear();
  }

  private static long toTicks(Duration duration) {
    return Math.max(0L, duration.toMillis() / 50L);
  }

  private record ActivePotionProjectile(String arenaId, ArenaPotionItemConfig config,
      String shooterName, String itemName) {
  }
}
