package com.talexck.minigamelib.core.world;

import org.bukkit.World;

import java.time.Duration;

public record RuntimeWorld(String templateWorldName, String runtimeWorldName, World world,
    Duration copyDuration, Duration loadDuration) {
}
