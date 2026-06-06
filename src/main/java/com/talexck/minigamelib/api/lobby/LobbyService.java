package com.talexck.minigamelib.api.lobby;

import org.bukkit.entity.Player;

import java.util.Optional;

public interface LobbyService {

  void configure(LobbySettings settings);

  Optional<LobbySettings> settings();

  boolean isLobbyWorld(String worldName);

  void teleportToSpawn(Player player);

}
