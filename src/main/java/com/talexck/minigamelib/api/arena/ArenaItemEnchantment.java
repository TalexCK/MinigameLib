package com.talexck.minigamelib.api.arena;

import java.util.Objects;

public record ArenaItemEnchantment(String key, int level) {

  public ArenaItemEnchantment {
    Objects.requireNonNull(key, "key");
    if (key.isBlank()) {
      throw new IllegalArgumentException("enchantment key cannot be blank");
    }
    if (level <= 0) {
      throw new IllegalArgumentException("enchantment level must be positive");
    }
  }
}
