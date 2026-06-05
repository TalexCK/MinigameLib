package com.talexck.minigamelib.core.world;

import java.time.Duration;
import java.util.logging.Logger;

public final class WorldOperationView {

  private final Logger logger;

  public WorldOperationView(Logger logger) {
    this.logger = logger;
  }

  public void renderCopyResult(WorldCopyResult result) {
    logger.info(() -> "Copied world template '%s' to '%s' in %d ms.".formatted(
        result.templateWorldName(), result.runtimeWorldName(), millis(result.duration())));
  }

  public void renderLoadResult(String worldName, Duration duration) {
    logger.info(() -> "Loaded world '%s' in %d ms.".formatted(worldName, millis(duration)));
  }

  public void renderUnloadResult(String worldName, boolean unloaded) {
    logger
        .info(() -> "Unload world '%s': %s.".formatted(worldName, unloaded ? "success" : "failed"));
  }

  private long millis(Duration duration) {
    return Math.max(0L, duration.toMillis());
  }
}
