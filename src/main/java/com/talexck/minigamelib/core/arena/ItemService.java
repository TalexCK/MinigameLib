package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaItemEntry;
import com.talexck.minigamelib.api.arena.ArenaItemMode;
import com.talexck.minigamelib.api.arena.ArenaTeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Owns all inventory-side item behaviour for arenas: granting beginning items, infinite block /
 * offhand maintenance, armour equipping, team-coloured items, and TNT auto-ignite. Also serves as
 * the shared item-matching utility for other gameplay services.
 */
final class ItemService implements Listener {

  private final JavaPlugin plugin;
  private final ArenaRegistry registry;
  private final java.util.Set<String> infinitePlacedBlocks = java.util.concurrent.ConcurrentHashMap
      .newKeySet();

  ItemService(JavaPlugin plugin, ArenaRegistry registry) {
    this.plugin = plugin;
    this.registry = registry;
    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  void giveBeginningItems(RuntimeArena arena) {
    if (arena.settings().beginningItems().isEmpty()) {
      return;
    }
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null) {
        continue;
      }
      player.getInventory().clear();
      for (ArenaItemEntry entry : arena.settings().beginningItems()) {
        ItemStack stack = createArenaItemStack(arena, playerName, entry);
        if (entry.mode() == ArenaItemMode.INFINITE_OFFHAND) {
          player.getInventory().setItemInOffHand(stack);
          continue;
        }
        if (equipArmor(player, stack)) {
          continue;
        }
        player.getInventory().addItem(stack);
      }
    }
  }

  ItemStack createArenaItemStack(RuntimeArena arena, String playerName, ArenaItemEntry entry) {
    ItemStack stack = entry.createStack();
    if (entry.mode() == ArenaItemMode.INFINITE) {
      Material material =
          arena.teamOf(playerName).map(TeamPalette::concrete).orElse(Material.WHITE_CONCRETE);
      stack = new ItemStack(material, 64);
      applyItemName(stack, entry.name());
    } else if (entry.mode() == ArenaItemMode.INFINITE_OFFHAND) {
      Material material =
          arena.teamOf(playerName).map(TeamPalette::concrete).orElse(Material.WHITE_CONCRETE);
      stack = new ItemStack(material, 64);
      applyItemName(stack, entry.name());
    } else if (entry.mode() == ArenaItemMode.TEAM_LEATHER_ARMOR) {
      Optional<ArenaTeamColor> teamColor = arena.teamOf(playerName);
      if (teamColor.isPresent()) {
        applyLeatherColor(stack, teamColor.get());
      }
    }
    return stack;
  }

  private boolean equipArmor(Player player, ItemStack stack) {
    return switch (stack.getType()) {
      case LEATHER_HELMET, CHAINMAIL_HELMET, IRON_HELMET, GOLDEN_HELMET, DIAMOND_HELMET, NETHERITE_HELMET, TURTLE_HELMET -> {
        player.getInventory().setHelmet(stack);
        yield true;
      }
      case LEATHER_CHESTPLATE, CHAINMAIL_CHESTPLATE, IRON_CHESTPLATE, GOLDEN_CHESTPLATE, DIAMOND_CHESTPLATE, NETHERITE_CHESTPLATE -> {
        player.getInventory().setChestplate(stack);
        yield true;
      }
      case LEATHER_LEGGINGS, CHAINMAIL_LEGGINGS, IRON_LEGGINGS, GOLDEN_LEGGINGS, DIAMOND_LEGGINGS, NETHERITE_LEGGINGS -> {
        player.getInventory().setLeggings(stack);
        yield true;
      }
      case LEATHER_BOOTS, CHAINMAIL_BOOTS, IRON_BOOTS, GOLDEN_BOOTS, DIAMOND_BOOTS, NETHERITE_BOOTS -> {
        player.getInventory().setBoots(stack);
        yield true;
      }
      default -> false;
    };
  }

  private void applyLeatherColor(ItemStack stack, ArenaTeamColor color) {
    ItemMeta meta = stack.getItemMeta();
    if (!(meta instanceof LeatherArmorMeta leatherMeta)) {
      return;
    }
    leatherMeta.setColor(TeamPalette.leather(color));
    stack.setItemMeta(leatherMeta);
  }

  private void applyItemName(ItemStack stack, String name) {
    if (name == null || name.isBlank()) {
      return;
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta != null) {
      meta.displayName(Component.text(name));
      stack.setItemMeta(meta);
    }
  }

  void clearArenaPlayerInventories(RuntimeArena arena) {
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null) {
        continue;
      }
      player.getInventory().clear();
      player.getInventory().setArmorContents(null);
      player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
    }
  }

  void startInfiniteBlockMaintenance(RuntimeArena arena) {
    BukkitTask task = new BukkitRunnable() {
      @Override
      public void run() {
        RuntimeArena current = registry.get(arena.arenaId());
        if (current == null || current.status() != com.talexck.minigamelib.api.arena.ArenaStatus.RUNNING) {
          cancel();
          return;
        }
        for (String playerName : current.playerNames()) {
          Player player = Bukkit.getPlayerExact(playerName);
          if (player == null) {
            continue;
          }
          current.settings().beginningItems().stream()
              .filter(entry -> entry.mode() == ArenaItemMode.INFINITE_OFFHAND).findFirst()
              .ifPresent(entry -> ensureInfiniteOffhand(current, player, entry));
        }
      }
    }.runTaskTimer(plugin, 1L, 5L);
    arena.boundaryTasks().add(task);
  }

  private void ensureInfiniteOffhand(RuntimeArena arena, Player player, ArenaItemEntry entry) {
    ItemStack offhand = player.getInventory().getItemInOffHand();
    Material material =
        arena.teamOf(player.getName()).map(TeamPalette::concrete).orElse(Material.WHITE_CONCRETE);
    if (offhand == null || offhand.getType() != material || offhand.getAmount() < 64) {
      player.getInventory().setItemInOffHand(createArenaItemStack(arena, player.getName(), entry));
    }
  }

  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    RuntimeArena arena = registry.findByPlayer(player.getName()).orElse(null);
    if (arena == null) {
      return;
    }
    ItemStack hand = event.getItemInHand();
    if (hand.getType() == Material.TNT && findIgniteTntItem(arena, hand).isPresent()) {
      ignitePlacedTnt(event.getBlockPlaced(), player);
    }
    Optional<ArenaItemEntry> infiniteEntry = findInfiniteBlockItem(arena, hand);
    if (infiniteEntry.isPresent()) {
      if (infiniteEntry.get().mode() == ArenaItemMode.INFINITE_OFFHAND
          && event.getHand() == EquipmentSlot.HAND) {
        event.setCancelled(true);
        clearMainHandInfiniteOffhandBlock(arena, player, infiniteEntry.get());
        ensureOffhandInfiniteBlock(arena, player, infiniteEntry.get());
        return;
      }
      infinitePlacedBlocks.add(blockKey(event.getBlockPlaced()));
      Bukkit.getScheduler().runTask(plugin,
          () -> refillInfiniteItem(arena, player, infiniteEntry.get(), event.getHand()));
    }
  }

  @EventHandler
  public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
    Player player = event.getPlayer();
    RuntimeArena arena = registry.findByPlayer(player.getName()).orElse(null);
    if (arena == null) {
      return;
    }
    ArenaItemEntry offhandEntry = arena.settings().beginningItems().stream()
        .filter(entry -> entry.mode() == ArenaItemMode.INFINITE_OFFHAND).findFirst().orElse(null);
    if (offhandEntry == null) {
      return;
    }
    boolean movingInfiniteBlock =
        findItemEntry(arena, event.getOffHandItem(), ArenaItemMode.INFINITE_OFFHAND).isPresent()
            || findItemEntry(arena, event.getMainHandItem(), ArenaItemMode.INFINITE_OFFHAND)
                .isPresent();
    if (!movingInfiniteBlock) {
      return;
    }
    event.setCancelled(true);
    clearMainHandInfiniteOffhandBlock(arena, player, offhandEntry);
    ensureOffhandInfiniteBlock(arena, player, offhandEntry);
  }

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    RuntimeArena arena = registry.findByPlayer(event.getPlayer().getName()).orElse(null);
    if (arena != null) {
      event.setDropItems(false);
      event.setExpToDrop(0);
    }
    String key = blockKey(event.getBlock());
    if (infinitePlacedBlocks.remove(key)) {
      event.setDropItems(false);
    }
  }

  Optional<ArenaItemEntry> findItemEntry(RuntimeArena arena, ItemStack stack, ArenaItemMode mode) {
    if (stack == null || stack.getType().isAir()) {
      return Optional.empty();
    }
    return allConfiguredItems(arena).stream().filter(entry -> entry.mode() == mode)
        .filter(entry -> matchesArenaItem(arena, stack, entry)).findFirst();
  }

  private List<ArenaItemEntry> allConfiguredItems(RuntimeArena arena) {
    List<ArenaItemEntry> items = new ArrayList<>(arena.settings().beginningItems());
    arena.settings().lootChests().stream().flatMap(chest -> chest.lootTable().stream())
        .flatMap(entry -> entry.items().stream()).forEach(items::add);
    return items;
  }

  private Optional<ArenaItemEntry> findIgniteTntItem(RuntimeArena arena, ItemStack stack) {
    if (stack == null || stack.getType() != Material.TNT) {
      return Optional.empty();
    }
    return allConfiguredItems(arena).stream().filter(ArenaItemEntry::igniteTntOnPlace)
        .filter(entry -> entry.item().getType() == Material.TNT)
        .filter(entry -> matchesArenaItem(arena, stack, entry)).findFirst();
  }

  private void ignitePlacedTnt(Block block, Player source) {
    org.bukkit.Location location = block.getLocation().add(0.5, 0.0, 0.5);
    block.setType(Material.AIR);
    block.getWorld().spawn(location, TNTPrimed.class, tnt -> tnt.setSource(source));
  }

  private boolean matchesArenaItem(RuntimeArena arena, ItemStack stack, ArenaItemEntry entry) {
    if (entry.mode() == ArenaItemMode.INFINITE || entry.mode() == ArenaItemMode.INFINITE_OFFHAND) {
      return isConcrete(stack.getType());
    }
    if (stack.getType() != entry.item().getType()) {
      return false;
    }
    if (entry.mode() == ArenaItemMode.POTION || entry.mode() == ArenaItemMode.SELF_POTION
        || entry.mode() == ArenaItemMode.TEAM_LEATHER_ARMOR) {
      return itemDisplayName(stack).equals(entry.name());
    }
    return true;
  }

  private String itemDisplayName(ItemStack stack) {
    ItemMeta meta = stack.getItemMeta();
    if (meta == null || !meta.hasDisplayName() || meta.displayName() == null) {
      return "";
    }
    return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
  }

  private void refillInfiniteItem(RuntimeArena arena, Player player) {
    ArenaItemEntry entry = arena.settings().beginningItems().stream()
        .filter(item -> item.mode() == ArenaItemMode.INFINITE
            || item.mode() == ArenaItemMode.INFINITE_OFFHAND)
        .findFirst().orElse(null);
    if (entry == null) {
      return;
    }
    Material material =
        arena.teamOf(player.getName()).map(TeamPalette::concrete).orElse(Material.WHITE_CONCRETE);
    if (entry.mode() == ArenaItemMode.INFINITE_OFFHAND) {
      player.getInventory().setItemInOffHand(createArenaItemStack(arena, player.getName(), entry));
      return;
    }
    for (ItemStack stack : player.getInventory().getContents()) {
      if (stack != null && stack.getType() == material) {
        stack.setAmount(64);
        return;
      }
    }
    player.getInventory().addItem(createArenaItemStack(arena, player.getName(), entry));
  }

  private Optional<ArenaItemEntry> findInfiniteBlockItem(RuntimeArena arena, ItemStack stack) {
    Optional<ArenaItemEntry> mainHandEntry = findItemEntry(arena, stack, ArenaItemMode.INFINITE);
    if (mainHandEntry.isPresent()) {
      return mainHandEntry;
    }
    return findItemEntry(arena, stack, ArenaItemMode.INFINITE_OFFHAND);
  }

  private void refillInfiniteItem(RuntimeArena arena, Player player, ArenaItemEntry entry,
      EquipmentSlot hand) {
    ItemStack refill = createArenaItemStack(arena, player.getName(), entry);
    if (entry.mode() == ArenaItemMode.INFINITE_OFFHAND || hand == EquipmentSlot.OFF_HAND) {
      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        clearMainHandInfiniteOffhandBlock(arena, player, entry);
        player.getInventory().setItemInOffHand(refill);
      }, 1L);
      return;
    }
    if (hand == EquipmentSlot.HAND) {
      Bukkit.getScheduler().runTaskLater(plugin,
          () -> player.getInventory().setItemInMainHand(refill), 1L);
      return;
    }
    refillInfiniteItem(arena, player);
  }

  private void ensureOffhandInfiniteBlock(RuntimeArena arena, Player player, ArenaItemEntry entry) {
    Bukkit.getScheduler().runTask(plugin, () -> player.getInventory()
        .setItemInOffHand(createArenaItemStack(arena, player.getName(), entry)));
  }

  private void clearMainHandInfiniteOffhandBlock(RuntimeArena arena, Player player,
      ArenaItemEntry entry) {
    if (entry.mode() != ArenaItemMode.INFINITE_OFFHAND) {
      return;
    }
    ItemStack mainHand = player.getInventory().getItemInMainHand();
    if (findItemEntry(arena, mainHand, ArenaItemMode.INFINITE_OFFHAND).isPresent()) {
      player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
    }
  }

  void consumeOne(ItemStack item) {
    if (item.getAmount() <= 1) {
      item.setAmount(0);
      return;
    }
    item.setAmount(item.getAmount() - 1);
  }

  private String blockKey(Block block) {
    return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
  }

  private boolean isConcrete(Material material) {
    return material.name().endsWith("_CONCRETE");
  }

  void shutdown() {
    HandlerList.unregisterAll(this);
  }
}
