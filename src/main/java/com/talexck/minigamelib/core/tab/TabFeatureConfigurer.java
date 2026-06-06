package com.talexck.minigamelib.core.tab;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class TabFeatureConfigurer {

  private final JavaPlugin plugin;

  public TabFeatureConfigurer(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  public void ensureEnabled() {
    File configFile = new File("plugins/TAB/config.yml");
    if (!configFile.isFile()) {
      plugin.getLogger().warning("TAB config.yml 未找到，MinigameLib 无法自动启用 TAB layout。");
      return;
    }
    YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
    boolean changed = false;
    for (String path : List.of("layout.enabled", "header-footer.enabled",
        "scoreboard.enabled", "bossbar.enabled")) {
      if (!config.getBoolean(path, false)) {
        config.set(path, true);
        changed = true;
      }
    }
    changed |= setIfDifferent(config, "layout.empty-slot-ping-value", 1);
    changed |= ensurePlayerlistObjectiveDisabled(config);
    changed |= setIfDifferent(config, "ping-spoof.enabled", false);
    changed |= clearMapIfNotEmpty(config, "bossbar.bossbars");
    changed |= clearMapIfNotEmpty(config, "bossbar.bars");
    if (config.contains("bossbar.default-bars")) {
      config.set("bossbar.default-bars", null);
      changed = true;
    }
    changed |= clearMapIfNotEmpty(config, "scoreboard.scoreboards");
    changed |= removeBrokenScoreboards(config);
    if (!changed) {
      return;
    }
    try {
      config.save(configFile);
    } catch (IOException exception) {
      plugin.getLogger().warning("TAB config.yml 保存失败：" + exception.getMessage());
      return;
    }
    Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.dispatchCommand(
        Bukkit.getConsoleSender(), "tab reload"), 20L);
    plugin.getLogger().info("已自动修正 TAB layout/header-footer/scoreboard/bossbar 配置，并请求 TAB reload。");
  }

  private boolean setIfDifferent(YamlConfiguration config, String path, Object value) {
    Object current = config.get(path);
    if (value.equals(current)) {
      return false;
    }
    config.set(path, value);
    return true;
  }

  private boolean clearMapIfNotEmpty(YamlConfiguration config, String path) {
    ConfigurationSection section = config.getConfigurationSection(path);
    if (section == null || section.getKeys(false).isEmpty()) {
      return false;
    }
    config.set(path, new java.util.LinkedHashMap<>());
    return true;
  }

  private boolean ensurePlayerlistObjectiveDisabled(YamlConfiguration config) {
    boolean changed = false;
    if (!config.isConfigurationSection("playerlist-objective")) {
      config.set("playerlist-objective", new java.util.LinkedHashMap<>());
      changed = true;
    }
    changed |= setIfDifferent(config, "playerlist-objective.enabled", false);
    changed |= setIfDifferent(config, "playerlist-objective.value", "0");
    changed |= setIfDifferent(config, "playerlist-objective.fancy-value", "&7Ping: %ping%");
    changed |= setIfDifferent(config, "playerlist-objective.disable-condition",
        "%world%=disabledworld");
    if (config.contains("player-list-objective")) {
      config.set("player-list-objective", null);
      changed = true;
    }
    return changed;
  }

  private boolean removeBrokenScoreboards(YamlConfiguration config) {
    ConfigurationSection scoreboards = config.getConfigurationSection("scoreboard.scoreboards");
    if (scoreboards == null) {
      return false;
    }
    boolean changed = false;
    for (String key : List.copyOf(scoreboards.getKeys(false))) {
      ConfigurationSection section = scoreboards.getConfigurationSection(key);
      if (section == null) {
        continue;
      }
      boolean missingTitle = !section.isString("title");
      boolean missingLines = !section.isList("lines");
      if (missingTitle || missingLines) {
        scoreboards.set(key, null);
        plugin.getLogger().info("已删除 TAB 中格式不完整的 scoreboard 配置：" + key);
        changed = true;
      }
    }
    return changed;
  }
}
