package com.talexck.minigamelib.api.arena;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;

import java.util.Objects;

public record ArenaSound(
    Sound minecraftSound,
    String customSound,
    SoundCategory category,
    float volume,
    float pitch) {

  public ArenaSound {
    customSound = customSound == null ? "" : customSound;
    if (minecraftSound == null && customSound.isBlank()) {
      throw new IllegalArgumentException("minecraftSound or customSound must be provided");
    }
    if (category == null) {
      category = SoundCategory.MASTER;
    }
    if (volume < 0) {
      throw new IllegalArgumentException("volume cannot be negative");
    }
    if (pitch < 0) {
      throw new IllegalArgumentException("pitch cannot be negative");
    }
  }

  public static ArenaSound minecraft(Sound sound, float volume, float pitch) {
    return new ArenaSound(Objects.requireNonNull(sound, "sound"), "", SoundCategory.MASTER,
        volume, pitch);
  }

  public static ArenaSound custom(String sound, float volume, float pitch) {
    return new ArenaSound(null, sound, SoundCategory.MASTER, volume, pitch);
  }
}
