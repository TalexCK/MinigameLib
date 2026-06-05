package com.talexck.minigamelib.api.arena;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public record ArenaResourcePackConfig(
    boolean enabled,
    JavaPlugin ownerPlugin,
    String resourcePath,
    boolean required,
    String prompt,
    String publicUrlBase) {

  public ArenaResourcePackConfig {
    if (enabled) {
      Objects.requireNonNull(ownerPlugin, "ownerPlugin");
      Objects.requireNonNull(resourcePath, "resourcePath");
      if (resourcePath.isBlank()) {
        throw new IllegalArgumentException("resourcePath cannot be blank");
      }
    }
    prompt = prompt == null ? "" : prompt;
    publicUrlBase = publicUrlBase == null ? "" : publicUrlBase;
  }

  public static ArenaResourcePackConfig disabled() {
    return new ArenaResourcePackConfig(false, null, "", true, "", "");
  }
}
