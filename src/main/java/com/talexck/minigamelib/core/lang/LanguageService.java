package com.talexck.minigamelib.core.lang;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Loads MinigameLib's own player-visible texts. The language is chosen by {@code language} in
 * {@code plugins/MinigameLib/config.yml}; missing keys fall back to the bundled file so upgrades
 * never show raw keys.
 */
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
    plugin.reloadConfig();
    String language = plugin.getConfig().getString("language", DEFAULT_LANGUAGE);
    String resourcePath = "lang/" + language + ".yml";
    if (plugin.getResource(resourcePath) == null
        && !new File(plugin.getDataFolder(), resourcePath).isFile()) {
      plugin.getLogger().warning("Unknown language " + language + ", falling back to "
          + DEFAULT_LANGUAGE);
      resourcePath = "lang/" + DEFAULT_LANGUAGE + ".yml";
    }
    File file = new File(plugin.getDataFolder(), resourcePath);
    if (!file.isFile() && plugin.getResource(resourcePath) != null) {
      plugin.saveResource(resourcePath, false);
    }
    YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
    YamlConfiguration defaults = bundled(resourcePath);
    if (defaults != null) {
      loaded.setDefaults(defaults);
    }
    this.messages = loaded;
  }

  private YamlConfiguration bundled(String resourcePath) {
    InputStream stream = plugin.getResource(resourcePath);
    if (stream == null) {
      return null;
    }
    try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      return YamlConfiguration.loadConfiguration(reader);
    } catch (java.io.IOException exception) {
      return null;
    }
  }

  public String text(String key) {
    return messages.getString(key, key);
  }

  public String text(String key, Object... replacements) {
    String value = text(key);
    for (int index = 0; index + 1 < replacements.length; index += 2) {
      value = value.replace(String.valueOf(replacements[index]),
          String.valueOf(replacements[index + 1]));
    }
    return value;
  }

  public List<String> list(String key) {
    List<String> values = messages.getStringList(key);
    return values.isEmpty() ? List.of(key) : values;
  }
}
