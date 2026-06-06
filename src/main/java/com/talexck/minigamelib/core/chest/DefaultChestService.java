package com.talexck.minigamelib.core.chest;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.function.BiConsumer;

public final class DefaultChestService {

  private final ChestController controller;

  public DefaultChestService(JavaPlugin plugin) {
    this.controller = new ChestController(plugin);
  }

  public void startArenaChests(String arenaId, World world, List<ChestDefinition> definitions,
      BiConsumer<ChestDefinition, Location> generatedCallback) {
    controller.startArenaChests(arenaId, world, definitions, generatedCallback);
  }

  public void stopArenaChests(String arenaId, boolean destroyChests) {
    controller.stopArenaChests(arenaId, destroyChests);
  }

  public boolean isActiveChest(Block block) {
    return controller.isActiveChest(block);
  }
}
