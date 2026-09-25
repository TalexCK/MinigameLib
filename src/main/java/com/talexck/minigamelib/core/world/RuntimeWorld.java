package com.talexck.minigamelib.core.world;

import org.bukkit.World;

import java.time.Duration;

/**
 * A loaded runtime copy of a template world.
 *
 * @param worldPath the folder the server actually uses for this world. On 26.1+ servers a legacy
 *     world folder is migrated on load, so this can differ from {@code <container>/<name>}.
 */
public record RuntimeWorld(String templateWorldName, String runtimeWorldName, World world,
    Duration copyDuration, Duration loadDuration, java.nio.file.Path worldPath) {
}
