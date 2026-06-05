package com.talexck.minigamelib.api.arena;

import java.time.Duration;
import java.util.Objects;

public record ArenaTitleFrame(
    String title,
    String subtitle,
    Duration fadeIn,
    Duration stay,
    Duration fadeOut) {

  public ArenaTitleFrame {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(subtitle, "subtitle");
    Objects.requireNonNull(fadeIn, "fadeIn");
    Objects.requireNonNull(stay, "stay");
    Objects.requireNonNull(fadeOut, "fadeOut");
    if (fadeIn.isNegative() || stay.isNegative() || fadeOut.isNegative()) {
      throw new IllegalArgumentException("title durations cannot be negative");
    }
  }

  public static ArenaTitleFrame empty() {
    return new ArenaTitleFrame("", "", Duration.ZERO, Duration.ZERO, Duration.ZERO);
  }
}
