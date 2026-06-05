package com.talexck.minigamelib.api.setup;

import org.bukkit.entity.Player;

public interface SetupService {

  void startBlockMarker(Player player, SetupBlockMarkListener listener);

  void stopBlockMarker(Player player);

  boolean isBlockMarkerActive(Player player);
}
