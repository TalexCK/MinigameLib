package com.talexck.minigamelib.core.stats;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;

import java.util.List;

/**
 * All DecentHolograms calls live here so the rest of MinigameLib never loads DecentHolograms
 * classes. Only touch this class after checking that the plugin is enabled.
 */
final class DecentHologramsBridge {

  private DecentHologramsBridge() {
  }

  static void render(String name, Location location, List<String> lines) {
    Hologram hologram = DHAPI.getHologram(name);
    if (hologram == null) {
      DHAPI.createHologram(name, location, false, lines);
      return;
    }
    DHAPI.moveHologram(hologram, location);
    DHAPI.setHologramLines(hologram, lines);
  }

  static void remove(String name) {
    DHAPI.removeHologram(name);
  }
}
