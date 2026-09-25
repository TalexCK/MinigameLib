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
    String itemModelKey,
    boolean clearNegativeEffects,
    boolean affectEachPlayerOnce,
    Duration fuse) {

  public ArenaPotionItemConfig(double radius, Duration duration, PotionEffectType effectType,
      int amplifier, Duration effectDuration, int projectileCustomModelData, String itemModelKey) {
    this(radius, duration, effectType, amplifier, effectDuration, projectileCustomModelData,
        itemModelKey, false, false, Duration.ZERO);
  }

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
    if (effectType == null && !clearNegativeEffects) {
      throw new NullPointerException("effectType");
    }
    Objects.requireNonNull(effectDuration, "effectDuration");
    itemModelKey = itemModelKey == null ? "" : itemModelKey;
    fuse = fuse == null ? Duration.ZERO : fuse;
    if (radius <= 0) {
      throw new IllegalArgumentException("radius must be positive");
    }
    if (duration.isNegative() || effectDuration.isNegative() || fuse.isNegative()) {
      throw new IllegalArgumentException("potion durations cannot be negative");
    }
    if (amplifier < 0) {
      throw new IllegalArgumentException("amplifier cannot be negative");
    }
  }
}
