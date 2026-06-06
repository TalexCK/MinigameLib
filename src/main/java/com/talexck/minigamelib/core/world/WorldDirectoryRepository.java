package com.talexck.minigamelib.core.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;

public final class WorldDirectoryRepository {

  private static final Set<String> SKIPPED_FILES = Set.of("uid.dat", "session.lock");
  private static final String TEMPLATE_WORLD_FOLDER = "arena";

  private final Path worldContainer;

  public WorldDirectoryRepository(Path worldContainer) {
    this.worldContainer = worldContainer;
  }

  public WorldCopyResult copyTemplateWorld(WorldCreateRequest request, Instant startedAt) {
    Path source = worldContainer.resolve(TEMPLATE_WORLD_FOLDER).resolve(request.templateWorldName());
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

  public boolean deleteWorldDirectory(String worldName) {
    if (worldName == null || worldName.isBlank() || worldName.equals("world")
        || worldName.contains("/") || worldName.contains("\\") || worldName.equals(".")
        || worldName.equals("..")) {
      throw new IllegalArgumentException("Unsafe world directory: " + worldName);
    }
    Path normalizedContainer = worldContainer.toAbsolutePath().normalize();
    Path target = normalizedContainer.resolve(worldName).normalize();
    if (target.equals(normalizedContainer) || !target.startsWith(normalizedContainer)) {
      throw new IllegalArgumentException("Unsafe world directory: " + worldName);
    }
    if (!Files.exists(target)) {
      return false;
    }
    try (var stream = Files.walk(target)) {
      for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
      return true;
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to delete world: " + worldName, exception);
    }
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
