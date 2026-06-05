package com.talexck.minigamelib.core.world;

import java.util.Objects;

public record WorldCreateRequest(String templateWorldName, String runtimeWorldName) {

  public WorldCreateRequest {
    Objects.requireNonNull(templateWorldName, "templateWorldName");
    Objects.requireNonNull(runtimeWorldName, "runtimeWorldName");
    if (templateWorldName.isBlank()) {
      throw new IllegalArgumentException("templateWorldName cannot be blank");
    }
    if (runtimeWorldName.isBlank()) {
      throw new IllegalArgumentException("runtimeWorldName cannot be blank");
    }
  }
}
