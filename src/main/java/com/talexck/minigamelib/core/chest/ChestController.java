package com.talexck.minigamelib.core.chest;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

public final class ChestController {

  private static final long DEFAULT_REGENERATION_PERIOD_TICKS = 20L * 60L;
  private static final long DEFAULT_DESTRUCTION_DELAY_TICKS = 20L * 60L;

  private final JavaPlugin plugin;
  private final Random random = new Random();
  private final ConcurrentMap<String, ArenaChestSession> sessions = new ConcurrentHashMap<>();

  public ChestController(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  public void startArenaChests(String arenaId, World world, List<ChestDefinition> definitions,
      BiConsumer<ChestDefinition, Location> generatedCallback) {
    Objects.requireNonNull(arenaId, "arenaId");
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(definitions, "definitions");

    stopArenaChests(arenaId, true);

    ArenaChestSession session = new ArenaChestSession(world, List.copyOf(definitions));
    sessions.put(arenaId, session);
    generateRound(session, 0, generatedCallback);
    scheduleRegeneration(arenaId, session, generatedCallback);
    scheduleDestruction(arenaId, session);
  }

  public void stopArenaChests(String arenaId, boolean destroyChests) {
    ArenaChestSession session = sessions.remove(arenaId);
    if (session == null) {
      return;
    }

    session.tasks().forEach(BukkitTask::cancel);
    if (destroyChests) {
      destroyChests(session);
    }
  }

  private void scheduleRegeneration(String arenaId, ArenaChestSession session,
      BiConsumer<ChestDefinition, Location> generatedCallback) {
    for (ChestDefinition definition : session.definitions()) {
      if (!definition.timedRegeneration()) {
        continue;
      }
      long periodTicks = resolveRegenerationPeriod(definition);
      BukkitTask task = new BukkitRunnable() {
        @Override
        public void run() {
          ArenaChestSession current = sessions.get(arenaId);
          if (current == null) {
            cancel();
            return;
          }
          int nextRound = current.nextRound();
          generateDefinition(current, definition, nextRound, generatedCallback);
        }
      }.runTaskTimer(plugin, periodTicks, periodTicks);
      session.tasks().add(task);
    }
  }

  private void scheduleDestruction(String arenaId, ArenaChestSession session) {
    for (ChestDefinition definition : session.definitions()) {
      if (!definition.timedDestruction()) {
        continue;
      }
      BukkitTask task = new BukkitRunnable() {
        @Override
        public void run() {
          ArenaChestSession current = sessions.get(arenaId);
          if (current == null) {
            cancel();
            return;
          }
          destroyChest(current.world(), definition);
        }
      }.runTaskLater(plugin, resolveDestructionDelay(definition));
      session.tasks().add(task);
    }
  }

  private void generateRound(ArenaChestSession session, int round,
      BiConsumer<ChestDefinition, Location> generatedCallback) {
    for (ChestDefinition definition : session.definitions()) {
      if (round > 0 && !definition.timedRegeneration()) {
        continue;
      }
      generateDefinition(session, definition, round, generatedCallback);
    }
  }

  private void generateDefinition(ArenaChestSession session, ChestDefinition definition, int round,
      BiConsumer<ChestDefinition, Location> generatedCallback) {
    Location location = definition.position().toLocation(session.world());
    fillChest(location, selectLoot(definition, round), definition);
    generatedCallback.accept(definition, location);
  }

  private List<ItemStack> selectLoot(ChestDefinition definition, int round) {
    List<ChestLootEntry> eligibleEntries =
        definition.lootTable().stream().filter(entry -> entry.earliestGenerationRound() <= round)
            .toList();

    List<ItemStack> selected = new ArrayList<>();
    List<ChestLootEntry> pool = new ArrayList<>(eligibleEntries);
    int minItems = Math.min(definition.minItems(), pool.size());
    int maxItems = Math.min(definition.maxItems(), pool.size());
    int itemCount = minItems >= maxItems
        ? minItems
        : minItems + random.nextInt(maxItems - minItems + 1);
    for (int index = 0; index < itemCount; index++) {
      ChestLootEntry entry = takeWeighted(pool);
      if (entry == null) {
        break;
      }
      entry.items().stream().map(com.talexck.minigamelib.api.arena.ArenaItemEntry::createStack)
          .forEach(selected::add);
      pool.remove(entry);
    }
    return selected;
  }

  private ChestLootEntry takeWeighted(List<ChestLootEntry> entries) {
    double totalWeight = entries.stream().mapToDouble(ChestLootEntry::weight).sum();
    if (totalWeight <= 0) {
      return null;
    }
    double cursor = random.nextDouble(totalWeight);
    for (ChestLootEntry entry : entries) {
      cursor -= entry.weight();
      if (cursor <= 0) {
        return entry;
      }
    }
    return entries.get(entries.size() - 1);
  }

  private void fillChest(Location location, List<ItemStack> loot, ChestDefinition definition) {
    Block block = location.getBlock();
    block.setType(Material.CHEST);
    if (!(block.getState() instanceof Chest chest)) {
      return;
    }
    if (!definition.displayName().isBlank()) {
      chest.customName(Component.text(definition.displayName()));
      chest.update(true);
    }

    Inventory inventory = chest.getBlockInventory();
    inventory.clear();
    List<Integer> slots = resolveSlots(inventory.getSize(), loot.size(), definition.placementMode());
    for (int index = 0; index < loot.size() && index < slots.size(); index++) {
      inventory.setItem(slots.get(index), loot.get(index));
    }
  }

  private List<Integer> resolveSlots(int inventorySize, int lootSize, ChestPlacementMode mode) {
    ChestPlacementMode resolvedMode = mode == ChestPlacementMode.AUTO
        ? (lootSize <= 1 ? ChestPlacementMode.CENTER : ChestPlacementMode.MIRRORED)
        : mode;
    if (resolvedMode == ChestPlacementMode.CENTER) {
      return List.of(inventorySize / 2);
    }
    return mirroredSlots(inventorySize, lootSize);
  }

  private List<Integer> mirroredSlots(int inventorySize, int lootSize) {
    int center = inventorySize / 2;
    List<Integer> slots = new ArrayList<>();
    if (lootSize % 2 == 1) {
      slots.add(center);
    }
    for (int offset = 1; slots.size() < lootSize && center - offset >= 0
        && center + offset < inventorySize; offset++) {
      slots.add(center - offset);
      if (slots.size() < lootSize) {
        slots.add(center + offset);
      }
    }
    return slots;
  }

  private void destroyChests(ArenaChestSession session) {
    for (ChestDefinition definition : session.definitions()) {
      destroyChest(session.world(), definition);
    }
  }

  private void destroyChest(World world, ChestDefinition definition) {
    Block block = definition.position().toLocation(world).getBlock();
    if (block.getType() == Material.CHEST) {
      block.setType(Material.AIR);
    }
  }

  private long resolveRegenerationPeriod(ChestDefinition definition) {
    return definition.regenerationPeriodTicks() > 0 ? definition.regenerationPeriodTicks()
        : DEFAULT_REGENERATION_PERIOD_TICKS;
  }

  private long resolveDestructionDelay(ChestDefinition definition) {
    return definition.destructionDelayTicks() > 0 ? definition.destructionDelayTicks()
        : DEFAULT_DESTRUCTION_DELAY_TICKS;
  }

  private static final class ArenaChestSession {

    private final World world;
    private final List<ChestDefinition> definitions;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private int round;

    private ArenaChestSession(World world, List<ChestDefinition> definitions) {
      this.world = world;
      this.definitions = definitions;
    }

    private World world() {
      return world;
    }

    private List<ChestDefinition> definitions() {
      return definitions;
    }

    private List<BukkitTask> tasks() {
      return tasks;
    }

    private int nextRound() {
      round++;
      return round;
    }
  }
}
