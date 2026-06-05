package com.talexck.minigamelib;

import com.talexck.minigamelib.api.MinigameLibrary;
import com.talexck.minigamelib.core.MinigameLibraryImpl;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;

public final class MinigameLibPlugin extends JavaPlugin {

  private MinigameLibraryImpl library;

  @Override
  public void onEnable() {
    this.library = new MinigameLibraryImpl(this);
    Bukkit.getServicesManager().register(MinigameLibrary.class, library, this,
        ServicePriority.Normal);
    getLogger().info("MinigameLib Plugin enabled.");
  }

  @Override
  public void onDisable() {
    if (library != null) {
      Bukkit.getServicesManager().unregister(MinigameLibrary.class, library);
      library.shutdown();
    }
    getLogger().info("MinigameLib Plugin disabled.");
  }
}
