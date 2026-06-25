package com.talexck.minigamelib.core.lang;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class LanguageService {

  private final JavaPlugin plugin;
  private YamlConfiguration messages;

  public LanguageService(JavaPlugin plugin) {
    this.plugin = plugin;
    reload();
  }

  public void reload() {
    String resourcePath = "lang/zh_cn.yml";
    File file = new File(plugin.getDataFolder(), resourcePath);
    if (!file.isFile()) {
      plugin.saveResource(resourcePath, false);
    }
    this.messages = YamlConfiguration.loadConfiguration(file);
  }

  public String text(String key) {
    return messages.getString(key, key);
  }
}
