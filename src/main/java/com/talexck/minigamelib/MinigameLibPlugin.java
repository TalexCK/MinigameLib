package com.talexck.minigamelib;

import org.bukkit.plugin.java.JavaPlugin;

public final class MinigameLibPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("MinigameLib enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MinigameLib disabled.");
    }
}
