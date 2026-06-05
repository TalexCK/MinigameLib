package com.talexck.minigamelib.core.world;

import java.nio.file.Path;
import java.time.Duration;

public record WorldCopyResult(String templateWorldName, String runtimeWorldName, Path source,
    Path target, Duration duration) {
}
