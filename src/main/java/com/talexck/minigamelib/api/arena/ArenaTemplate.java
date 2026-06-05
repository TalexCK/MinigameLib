package com.talexck.minigamelib.api.arena;

import java.util.Objects;

public record ArenaTemplate(
    String templateId,
    String templateWorldName,
    ArenaLayout defaultLayout,
    ArenaSettings defaultSettings,
    ArenaLifecycleListener defaultListener) {

  public ArenaTemplate {
    Objects.requireNonNull(templateId, "templateId");
    Objects.requireNonNull(templateWorldName, "templateWorldName");
    Objects.requireNonNull(defaultLayout, "defaultLayout");
    Objects.requireNonNull(defaultSettings, "defaultSettings");
    if (templateId.isBlank()) {
      throw new IllegalArgumentException("templateId cannot be blank");
    }
    if (templateWorldName.isBlank()) {
      throw new IllegalArgumentException("templateWorldName cannot be blank");
    }
    if (defaultListener == null) {
      defaultListener = ArenaLifecycleListener.noop();
    }
  }
}
