package com.talexck.minigamelib.api.stats;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record StatsBoard(
    LeaderboardType type,
    String id,
    String worldName,
    double x,
    double y,
    double z,
    float yaw,
    float pitch) {

  public static StatsBoard fromLocation(LeaderboardType type, String id, Location location) {
    if (location == null || location.getWorld() == null) {
      throw new IllegalArgumentException("location world cannot be null");
    }
    return new StatsBoard(type, id, location.getWorld().getName(), location.getX(),
        location.getY(), location.getZ(), location.getYaw(), location.getPitch());
  }

  public Location toLocation() {
    World world = Bukkit.getWorld(worldName);
    return world == null ? null : new Location(world, x, y, z, yaw, pitch);
  }
}
