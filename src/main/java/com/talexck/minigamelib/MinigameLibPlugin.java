package com.talexck.minigamelib;

import com.talexck.minigamelib.api.MinigameLibrary;
import com.talexck.minigamelib.core.MinigameLibraryImpl;
import com.talexck.minigamelib.core.lang.LanguageService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;

public final class MinigameLibPlugin extends JavaPlugin {

  private MinigameLibraryImpl library;
  private LanguageService language;

  @Override
  public void onEnable() {
    this.language = new LanguageService(this);
    this.library = new MinigameLibraryImpl(this, language);
    Bukkit.getServicesManager().register(MinigameLibrary.class, library, this,
        ServicePriority.Normal);
    getLogger().info(language.text("plugin.enabled"));
  }

  @Override
  public void onDisable() {
    if (library != null) {
      Bukkit.getServicesManager().unregister(MinigameLibrary.class, library);
      library.shutdown();
    }
    getLogger().info(language == null ? "MinigameLib Plugin disabled." : language.text("plugin.disabled"));
  }
}
