package com.talexck.minigamelib.api.setup;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface SetupService {

  /** Gives the player the marker axe; left-clicking a block with it calls the listener. */
  void startBlockMarker(Player player, SetupBlockMarkListener listener);

  /** Stops listening, removes the marker axe and every marker shown to the player. */
  void stopBlockMarker(Player player);

  boolean isBlockMarkerActive(Player player);

  /**
   * Shows a glowing block outline plus a floating label at a block, visible only to this player.
   * Showing a marker at a block that already has one replaces it.
   */
  void showMarker(Player player, Location block, String label, Color color);

  /** Removes the marker at a block, if any. */
  void removeMarker(Player player, Location block);

  /** Removes every marker shown to the player. */
  void clearMarkers(Player player);
}
