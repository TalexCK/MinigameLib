package com.talexck.minigamelib.api.arena;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import java.util.Objects;

public record ArenaBossBarConfig(
    boolean enabled,
    String title,
    BarColor color,
    BarStyle style,
    double runningProgress,
    boolean countdownProgress) {

  public ArenaBossBarConfig {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(color, "color");
    Objects.requireNonNull(style, "style");
    if (runningProgress < 0 || runningProgress > 1) {
      throw new IllegalArgumentException("runningProgress must be between 0 and 1");
    }
  }

  public static ArenaBossBarConfig disabled() {
    return new ArenaBossBarConfig(false, "", BarColor.WHITE, BarStyle.SOLID, 1.0, false);
  }
}
