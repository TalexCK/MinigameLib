package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaTeamColor;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import me.neznamy.tab.api.bossbar.BarColor;
import me.neznamy.tab.api.bossbar.BarStyle;

/**
 * Central mapping from {@link ArenaTeamColor} to the various concrete colour, material and
 * display representations used across the arena display, item and combat subsystems.
 *
 * <p>Display names are intentionally not held here: they are game-specific text and belong to the
 * consuming plugin's language files. This palette only maps to engine-level colour primitives.
 */
final class TeamPalette {

  private TeamPalette() {
  }

  static Material concrete(ArenaTeamColor color) {
    return switch (color) {
      case RED -> Material.RED_CONCRETE;
      case YELLOW -> Material.YELLOW_CONCRETE;
      case GREEN -> Material.GREEN_CONCRETE;
      case BLUE -> Material.BLUE_CONCRETE;
      case ORANGE -> Material.ORANGE_CONCRETE;
      case PURPLE -> Material.PURPLE_CONCRETE;
      case WHITE -> Material.WHITE_CONCRETE;
      case PINK -> Material.PINK_CONCRETE;
      case GRAY -> Material.GRAY_CONCRETE;
      case CYAN -> Material.CYAN_CONCRETE;
    };
  }

  static Color leather(ArenaTeamColor color) {
    return switch (color) {
      case RED -> Color.fromRGB(0xB02E26);
      case YELLOW -> Color.fromRGB(0xF1C232);
      case GREEN -> Color.fromRGB(0x5E7C16);
      case BLUE -> Color.fromRGB(0x3C44AA);
      case ORANGE -> Color.fromRGB(0xF9801D);
      case PURPLE -> Color.fromRGB(0x8932B8);
      case WHITE -> Color.fromRGB(0xF9FFFE);
      case PINK -> Color.fromRGB(0xF38BAA);
      case GRAY -> Color.fromRGB(0x474F52);
      case CYAN -> Color.fromRGB(0x169C9C);
    };
  }

  static NamedTextColor textColor(ArenaTeamColor color) {
    return switch (color) {
      case RED -> NamedTextColor.RED;
      case YELLOW -> NamedTextColor.YELLOW;
      case GREEN -> NamedTextColor.GREEN;
      case BLUE -> NamedTextColor.BLUE;
      case ORANGE -> NamedTextColor.GOLD;
      case PURPLE -> NamedTextColor.LIGHT_PURPLE;
      case WHITE -> NamedTextColor.WHITE;
      case PINK -> NamedTextColor.LIGHT_PURPLE;
      case GRAY -> NamedTextColor.GRAY;
      case CYAN -> NamedTextColor.AQUA;
    };
  }

  /** Legacy ampersand colour code (e.g. {@code &c}) for the team colour. */
  static String legacyCode(ArenaTeamColor color) {
    return switch (color) {
      case RED -> "&c";
      case YELLOW -> "&e";
      case GREEN -> "&a";
      case BLUE -> "&9";
      case ORANGE -> "&6";
      case PURPLE -> "&d";
      case WHITE -> "&f";
      case PINK -> "&d";
      case GRAY -> "&7";
      case CYAN -> "&b";
    };
  }

  static BarColor tabBarColor(org.bukkit.boss.BarColor color) {
    return switch (color) {
      case PINK -> BarColor.PINK;
      case BLUE -> BarColor.BLUE;
      case RED -> BarColor.RED;
      case GREEN -> BarColor.GREEN;
      case YELLOW -> BarColor.YELLOW;
      case PURPLE -> BarColor.PURPLE;
      case WHITE -> BarColor.WHITE;
    };
  }

  static BarStyle tabBarStyle(org.bukkit.boss.BarStyle style) {
    return switch (style) {
      case SOLID -> BarStyle.PROGRESS;
      case SEGMENTED_6 -> BarStyle.NOTCHED_6;
      case SEGMENTED_10 -> BarStyle.NOTCHED_10;
      case SEGMENTED_12 -> BarStyle.NOTCHED_12;
      case SEGMENTED_20 -> BarStyle.NOTCHED_20;
    };
  }
}
