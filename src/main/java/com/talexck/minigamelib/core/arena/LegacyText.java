package com.talexck.minigamelib.core.arena;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Shared helpers for converting legacy ampersand-coded strings to Adventure components/text. */
final class LegacyText {

  private LegacyText() {
  }

  /** Deserializes an {@code &}-coded string into a coloured component. */
  static Component component(String text) {
    return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
  }

  /** Converts an {@code &}-coded string into a {@code §}-coded legacy string. */
  static String legacySection(String text) {
    return LegacyComponentSerializer.legacySection()
        .serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(text));
  }
}
