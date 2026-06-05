package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaHandle;
import com.talexck.minigamelib.api.arena.ArenaLayout;
import com.talexck.minigamelib.api.arena.ArenaLifecycleListener;
import com.talexck.minigamelib.api.arena.ArenaSettings;
import com.talexck.minigamelib.api.arena.ArenaStatus;
import com.talexck.minigamelib.core.world.RuntimeWorld;
import org.bukkit.boss.BossBar;

import java.util.List;

final class RuntimeArena {

  private final String arenaId;
  private final String templateId;
  private final RuntimeWorld world;
  private final ArenaLayout layout;
  private final ArenaSettings settings;
  private final List<String> playerNames;
  private final ArenaLifecycleListener listener;
  private volatile ArenaStatus status = ArenaStatus.CREATED;
  private BossBar bossBar;

  RuntimeArena(String arenaId, String templateId, RuntimeWorld world, ArenaLayout layout,
      ArenaSettings settings, List<String> playerNames, ArenaLifecycleListener listener) {
    this.arenaId = arenaId;
    this.templateId = templateId;
    this.world = world;
    this.layout = layout;
    this.settings = settings;
    this.playerNames = List.copyOf(playerNames);
    this.listener = listener;
  }

  String arenaId() {
    return arenaId;
  }

  String templateId() {
    return templateId;
  }

  RuntimeWorld world() {
    return world;
  }

  ArenaLayout layout() {
    return layout;
  }

  ArenaSettings settings() {
    return settings;
  }

  List<String> playerNames() {
    return playerNames;
  }

  ArenaLifecycleListener listener() {
    return listener;
  }

  ArenaStatus status() {
    return status;
  }

  void setStatus(ArenaStatus status) {
    this.status = status;
  }

  BossBar bossBar() {
    return bossBar;
  }

  void setBossBar(BossBar bossBar) {
    this.bossBar = bossBar;
  }

  ArenaHandle handle() {
    return new ArenaHandle(arenaId, templateId, world.runtimeWorldName(), status, layout, settings,
        playerNames);
  }
}
