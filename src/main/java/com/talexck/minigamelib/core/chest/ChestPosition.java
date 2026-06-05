package com.talexck.minigamelib.core.chest;

import org.bukkit.Location;
import org.bukkit.World;

public record ChestPosition(double x, double y, double z) {

  public Location toLocation(World world) {
    return new Location(world, x, y, z);
  }
}
