package com.talexck.minigamelib.core.lang;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class LanguageService {

  private static final String DEFAULT_LANGUAGE = "zh_cn";

  private final JavaPlugin plugin;
  private YamlConfiguration messages;

  public LanguageService(JavaPlugin plugin) {
    this.plugin = plugin;
    reload();
  }

  public void reload() {
    plugin.saveDefaultConfig();
    String language = plugin.getConfig().getString("language", DEFAULT_LANGUAGE);
    String resourcePath = "lang/" + language + ".yml";
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
