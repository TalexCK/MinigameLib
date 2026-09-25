package com.talexck.minigamelib.core.setup;

import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.setup.SetupBlockMark;
import com.talexck.minigamelib.api.setup.SetupBlockMarkListener;
import com.talexck.minigamelib.api.setup.SetupService;
import com.talexck.minigamelib.core.lang.LanguageService;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultSetupService implements SetupService, Listener {

  private final LanguageService language;
  private final NamespacedKey markerToolKey;
  private final Map<UUID, SetupBlockMarkListener> blockMarkListeners = new ConcurrentHashMap<>();
  private final Map<UUID, Map<String, List<Entity>>> markers = new ConcurrentHashMap<>();
  private final JavaPlugin plugin;

  public DefaultSetupService(JavaPlugin plugin, LanguageService language) {
    this.plugin = plugin;
    this.language = language;
    this.markerToolKey = new NamespacedKey(plugin, "setup_marker_tool");
    plugin.getServer().getPluginManager().registerEvents(this, plugin);
  }

  @Override
  public void startBlockMarker(Player player, SetupBlockMarkListener listener) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(listener, "listener");
    blockMarkListeners.put(player.getUniqueId(), listener);
    player.getInventory().addItem(createMarkerAxe());
  }

  @Override
  public void stopBlockMarker(Player player) {
    Objects.requireNonNull(player, "player");
    blockMarkListeners.remove(player.getUniqueId());
    clearMarkers(player);
    for (ItemStack stack : player.getInventory().getContents()) {
      if (isMarkerAxe(stack)) {
        player.getInventory().remove(stack);
      }
    }
  }

  @Override
  public void showMarker(Player player, Location block, String label, Color color) {
    removeMarker(player, block);
    World world = block.getWorld();
    Location base = block.getBlock().getLocation();
    List<Entity> spawned = new ArrayList<>();
    BlockDisplay outline = world.spawn(base, BlockDisplay.class, display -> {
      display.setBlock(Material.GLASS.createBlockData());
      display.setTransformation(new Transformation(new Vector3f(-0.01f, -0.01f, -0.01f),
          new AxisAngle4f(), new Vector3f(1.02f, 1.02f, 1.02f), new AxisAngle4f()));
      display.setGlowing(true);
      display.setGlowColorOverride(color);
      configurePrivate(display);
    });
    spawned.add(outline);
    if (label != null && !label.isBlank()) {
      TextDisplay text = world.spawn(base.clone().add(0.5, 1.6, 0.5), TextDisplay.class, display -> {
        display.text(LegacyComponentSerializer.legacyAmpersand().deserialize(label));
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(true);
        configurePrivate(display);
      });
      spawned.add(text);
    }
    spawned.forEach(entity -> player.showEntity(plugin, entity));
    markers.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
        .put(key(base), spawned);
  }

  private void configurePrivate(Entity entity) {
    entity.setPersistent(false);
    entity.setVisibleByDefault(false);
  }

  @Override
  public void removeMarker(Player player, Location block) {
    Map<String, List<Entity>> playerMarkers = markers.get(player.getUniqueId());
    if (playerMarkers == null) {
      return;
    }
    List<Entity> removed = playerMarkers.remove(key(block.getBlock().getLocation()));
    if (removed != null) {
      removed.forEach(Entity::remove);
    }
  }

  @Override
  public void clearMarkers(Player player) {
    Map<String, List<Entity>> playerMarkers = markers.remove(player.getUniqueId());
    if (playerMarkers != null) {
      playerMarkers.values().forEach(list -> list.forEach(Entity::remove));
    }
  }

  private String key(Location location) {
    return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY()
        + ":" + location.getBlockZ();
  }

  /** Creative players would otherwise break the block they are trying to mark. */
  @EventHandler(ignoreCancelled = true)
  public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
    if (blockMarkListeners.containsKey(event.getPlayer().getUniqueId())
        && isMarkerAxe(event.getPlayer().getInventory().getItemInMainHand())) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    blockMarkListeners.remove(event.getPlayer().getUniqueId());
    clearMarkers(event.getPlayer());
  }

  @Override
  public boolean isBlockMarkerActive(Player player) {
    Objects.requireNonNull(player, "player");
    return blockMarkListeners.containsKey(player.getUniqueId());
  }

  public void shutdown() {
    HandlerList.unregisterAll(this);
    blockMarkListeners.clear();
    markers.values().forEach(map -> map.values().forEach(list -> list.forEach(Entity::remove)));
    markers.clear();
  }

  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null) {
      return;
    }
    SetupBlockMarkListener listener = blockMarkListeners.get(event.getPlayer().getUniqueId());
    if (listener == null || !isMarkerAxe(event.getItem())) {
      return;
    }
    event.setCancelled(true);
    Block block = event.getClickedBlock();
    Location location = block.getLocation();
    ArenaPoint point = new ArenaPoint(location.getX(), location.getY(), location.getZ(),
        location.getYaw(), location.getPitch());
    listener.onBlockMarked(new SetupBlockMark(event.getPlayer(), block, location, point));
  }

  private ItemStack createMarkerAxe() {
    ItemStack item = new ItemStack(Material.WOODEN_AXE);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.displayName(Component.text(language.text("setup.marker-tool-name")));
      meta.getPersistentDataContainer().set(markerToolKey, PersistentDataType.BYTE, (byte) 1);
      item.setItemMeta(meta);
    }
    return item;
  }

  private boolean isMarkerAxe(ItemStack item) {
    if (item == null || item.getType() != Material.WOODEN_AXE) {
      return false;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return false;
    }
    PersistentDataContainer container = meta.getPersistentDataContainer();
    return container.has(markerToolKey, PersistentDataType.BYTE);
  }
}
