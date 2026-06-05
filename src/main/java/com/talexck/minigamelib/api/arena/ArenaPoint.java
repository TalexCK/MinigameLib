package com.talexck.minigamelib.api.arena;

import org.bukkit.Location;
import org.bukkit.World;

public record ArenaPoint(double x, double y, double z, float yaw, float pitch) {

  public Location toLocation(World world) {
    return new Location(world, x, y, z, yaw, pitch);
  }
}
