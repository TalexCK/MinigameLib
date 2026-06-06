package com.talexck.minigamelib.core.chest;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
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

public final class ChestController implements Listener {

  private static final long DEFAULT_REGENERATION_PERIOD_TICKS = 20L * 60L;
  private static final long DEFAULT_DESTRUCTION_DELAY_TICKS = 20L * 60L;

  private final JavaPlugin plugin;
  private final Random random = new Random();
  private final ConcurrentMap<String, ArenaChestSession> sessions = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, String> activeChestArenaIds = new ConcurrentHashMap<>();

  public ChestController(JavaPlugin plugin) {
    this.plugin = plugin;
    plugin.getServer().getPluginManager().registerEvents(this, plugin);
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
    activeChestArenaIds.entrySet().removeIf(entry -> entry.getValue().equals(arenaId));
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
      selected.addAll(lootStacks(entry, definition.splitStacks()));
      pool.remove(entry);
    }
    return selected;
  }

  private List<ItemStack> lootStacks(ChestLootEntry entry, boolean chestSplitsStacks) {
    List<ItemStack> result = new ArrayList<>();
    List<com.talexck.minigamelib.api.arena.ArenaItemEntry> items = entry.items();
    if (!chestSplitsStacks) {
      for (com.talexck.minigamelib.api.arena.ArenaItemEntry item : items) {
        result.addAll(lootStacks(item, false));
      }
      return result;
    }
    for (int index = items.size() - 1; index >= 0; index--) {
      result.addAll(lootStacks(items.get(index), true));
    }
    return result;
  }

  private List<ItemStack> lootStacks(com.talexck.minigamelib.api.arena.ArenaItemEntry item,
      boolean chestSplitsStacks) {
    ItemStack stack = item.createStack();
    if (chestSplitsStacks && item.splitInLoot()) {
      return splitStack(stack);
    }
    return List.of(stack);
  }

  private List<ItemStack> splitStack(ItemStack stack) {
    List<ItemStack> result = new ArrayList<>();
    int amount = Math.max(1, stack.getAmount());
    for (int index = 0; index < amount; index++) {
      ItemStack single = stack.clone();
      single.setAmount(1);
      result.add(single);
    }
    return result;
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
    block.setType(resolveChestMaterial(definition.blockMaterial()));
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
    sessions.entrySet().stream()
        .filter(entry -> entry.getValue().world().equals(location.getWorld()))
        .findFirst()
        .ifPresent(entry -> activeChestArenaIds.put(blockKey(block), entry.getKey()));
  }

  private List<Integer> resolveSlots(int inventorySize, int lootSize, ChestPlacementMode mode) {
    ChestPlacementMode resolvedMode = mode == ChestPlacementMode.AUTO
        ? (lootSize <= 1 ? ChestPlacementMode.CENTER : ChestPlacementMode.MIRRORED)
        : mode;
    if (resolvedMode == ChestPlacementMode.CENTER) {
      return List.of(inventorySize / 2);
    }
    if (inventorySize >= 27) {
      return centeredSlots(lootSize);
    }
    return mirroredSlots(inventorySize, lootSize);
  }

  private List<Integer> centeredSlots(int lootSize) {
    return switch (lootSize) {
      case 0 -> List.of();
      case 1 -> List.of(13);
      case 2 -> List.of(12, 14);
      case 3 -> List.of(12, 13, 14);
      case 4 -> List.of(11, 12, 14, 15);
      case 5 -> List.of(4, 12, 13, 14, 22);
      case 6 -> List.of(3, 5, 12, 14, 21, 23);
      case 7 -> List.of(3, 4, 5, 12, 14, 21, 22);
      case 8 -> List.of(3, 4, 5, 12, 13, 14, 21, 22);
      case 9 -> List.of(3, 4, 5, 12, 13, 14, 21, 22, 23);
      case 10 -> List.of(2, 5, 11, 12, 13, 14, 20, 21, 22, 23);
      case 11 -> List.of(2, 4, 6, 11, 12, 13, 14, 20, 21, 22, 23);
      case 12 -> List.of(2, 3, 4, 5, 11, 12, 13, 14, 20, 21, 22, 23);
      default -> mirroredSlots(27, lootSize);
    };
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
    if (isActiveChest(block)) {
      block.setType(Material.AIR);
    }
    activeChestArenaIds.remove(blockKey(block));
  }

  public boolean isActiveChest(Block block) {
    return activeChestArenaIds.containsKey(blockKey(block));
  }

  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    scheduleDisappearIfEmpty(event.getInventory());
  }

  @EventHandler
  public void onInventoryDrag(InventoryDragEvent event) {
    scheduleDisappearIfEmpty(event.getInventory());
  }

  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    scheduleDisappearIfEmpty(event.getInventory());
  }

  private void scheduleDisappearIfEmpty(Inventory inventory) {
    Bukkit.getScheduler().runTask(plugin, () -> disappearIfEmpty(inventory));
  }

  private void disappearIfEmpty(Inventory inventory) {
    InventoryHolder holder = inventory.getHolder();
    if (!(holder instanceof Chest chest)) {
      return;
    }
    Block block = chest.getBlock();
    if (!isActiveChest(block) || !inventoryEmpty(inventory) || !inventory.getViewers().isEmpty()) {
      return;
    }
    activeChestArenaIds.remove(blockKey(block));
    block.setType(Material.AIR);
  }

  private boolean inventoryEmpty(Inventory inventory) {
    for (ItemStack stack : inventory.getContents()) {
      if (stack != null && !stack.getType().isAir()) {
        return false;
      }
    }
    return true;
  }

  private String blockKey(Block block) {
    return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":"
        + block.getZ();
  }

  private Material resolveChestMaterial(Material material) {
    if (material == null || !material.isBlock()) {
      return Material.CHEST;
    }
    return material;
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
