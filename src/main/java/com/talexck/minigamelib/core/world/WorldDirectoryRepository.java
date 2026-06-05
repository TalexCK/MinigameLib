package com.talexck.minigamelib.core.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

public final class WorldDirectoryRepository {

  private static final Set<String> SKIPPED_FILES = Set.of("uid.dat", "session.lock");

  private final Path worldContainer;

  public WorldDirectoryRepository(Path worldContainer) {
    this.worldContainer = worldContainer;
  }

  public WorldCopyResult copyTemplateWorld(WorldCreateRequest request, Instant startedAt) {
    Path source = worldContainer.resolve("world_templates").resolve(request.templateWorldName());
    Path target = worldContainer.resolve(request.runtimeWorldName());

    if (!Files.isDirectory(source)) {
      throw new IllegalArgumentException("Template world does not exist: " + source);
    }
    if (Files.exists(target)) {
      throw new IllegalArgumentException("Runtime world already exists: " + target);
    }

    try {
      copyDirectory(source, target);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to copy world: " + request.templateWorldName(),
          exception);
    }

    return new WorldCopyResult(request.templateWorldName(), request.runtimeWorldName(), source,
        target, Duration.between(startedAt, Instant.now()));
  }

  private void copyDirectory(Path source, Path target) throws IOException {
    try (var stream = Files.walk(source)) {
      for (Path sourcePath : stream.toList()) {
        Path relativePath = source.relativize(sourcePath);
        if (relativePath.getFileName() != null
            && SKIPPED_FILES.contains(relativePath.getFileName().toString())) {
          continue;
        }

        Path targetPath = target.resolve(relativePath);
        if (Files.isDirectory(sourcePath)) {
          Files.createDirectories(targetPath);
        } else {
          Files.createDirectories(targetPath.getParent());
          Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }
}
