package com.talexck.minigamelib.core.setup;

import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.setup.SetupBlockMark;
import com.talexck.minigamelib.api.setup.SetupBlockMarkListener;
import com.talexck.minigamelib.api.setup.SetupService;
import com.talexck.minigamelib.core.lang.LanguageService;
import org.bukkit.Location;
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

  public DefaultSetupService(JavaPlugin plugin, LanguageService language) {
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
  }

  @Override
  public boolean isBlockMarkerActive(Player player) {
    Objects.requireNonNull(player, "player");
    return blockMarkListeners.containsKey(player.getUniqueId());
  }

  public void shutdown() {
    HandlerList.unregisterAll(this);
    blockMarkListeners.clear();
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
