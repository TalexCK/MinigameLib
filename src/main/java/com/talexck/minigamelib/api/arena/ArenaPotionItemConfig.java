package com.talexck.minigamelib.api.arena;

import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.Objects;

public record ArenaPotionItemConfig(
    double radius,
    Duration duration,
    PotionEffectType effectType,
    int amplifier,
    Duration effectDuration,
    int projectileCustomModelData,
    String itemModelKey) {

  public ArenaPotionItemConfig(double radius, Duration duration, PotionEffectType effectType,
      int amplifier, Duration effectDuration) {
    this(radius, duration, effectType, amplifier, effectDuration, 0, "");
  }

  public ArenaPotionItemConfig(double radius, Duration duration, PotionEffectType effectType,
      int amplifier, Duration effectDuration, int projectileCustomModelData) {
    this(radius, duration, effectType, amplifier, effectDuration, projectileCustomModelData, "");
  }

  public ArenaPotionItemConfig {
    Objects.requireNonNull(duration, "duration");
    Objects.requireNonNull(effectType, "effectType");
    Objects.requireNonNull(effectDuration, "effectDuration");
    itemModelKey = itemModelKey == null ? "" : itemModelKey;
    if (radius <= 0) {
      throw new IllegalArgumentException("radius must be positive");
    }
    if (duration.isNegative() || effectDuration.isNegative()) {
      throw new IllegalArgumentException("potion durations cannot be negative");
    }
    if (amplifier < 0) {
      throw new IllegalArgumentException("amplifier cannot be negative");
    }
  }
}
