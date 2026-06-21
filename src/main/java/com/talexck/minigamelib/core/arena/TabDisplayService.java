package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaPlayerStats;
import com.talexck.minigamelib.api.arena.ArenaScoreboardConfig;
import com.talexck.minigamelib.api.arena.ArenaTeam;
import com.talexck.minigamelib.api.arena.ArenaTeamColor;
import com.talexck.minigamelib.api.arena.ArenaTeamSpawn;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.tablist.HeaderFooterManager;
import me.neznamy.tab.api.tablist.layout.Layout;
import me.neznamy.tab.api.tablist.layout.LayoutManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Encapsulates all TAB-plugin integration: the tablist layout (team panel, rankings, personal
 * stats), custom scoreboards, boss bars, and header/footer, plus the periodic lobby tablist reset.
 *
 * <p>The visual layout follows a Hypixel / MCC-style fixed grid: a framed header, left-hand team
 * roster columns, a centre live-ranking column, and a right-hand personal-stats column.
 */
final class TabDisplayService {

  private static final int TAB_LAYOUT_SIZE = 80;
  private static final int TAB_COLUMN_WIDTH = 28;

  private final JavaPlugin plugin;
  private final ArenaRegistry registry;
  private final Supplier<String> gameNameSupplier;
  private final java.util.Set<String> warnedTabFeatures =
      java.util.concurrent.ConcurrentHashMap.newKeySet();
  private final BukkitTask lobbyRefreshTask;

  TabDisplayService(JavaPlugin plugin, ArenaRegistry registry) {
    this.plugin = plugin;
    this.registry = registry;
    this.gameNameSupplier = TabDisplayService::resolveGameName;
    this.lobbyRefreshTask = Bukkit.getScheduler().runTaskTimer(plugin,
        this::resetLobbyTabViews, 40L, 40L);
  }

  // ---- Scoreboard ----------------------------------------------------------

  boolean applyScoreboard(RuntimeArena arena, int secondsLeft, ArenaTextRenderer renderer) {
    ArenaScoreboardConfig config = arena.settings().scoreboard();
    if (!config.enabled()) {
      return false;
    }
    try {
      me.neznamy.tab.api.scoreboard.ScoreboardManager manager =
          TabAPI.getInstance().getScoreboardManager();
      if (manager == null) {
        warnTabFeatureOnce("scoreboard");
        return false;
      }
      String name = "mgl-scoreboard-" + arena.arenaId();
      me.neznamy.tab.api.scoreboard.Scoreboard scoreboard =
          manager.getRegisteredScoreboards().get(name);
      List<String> lines = config.lines().stream()
          .map(line -> LegacyText.legacySection(renderer.render(arena, line, secondsLeft, null)))
          .toList();
      String title =
          LegacyText.legacySection(renderer.render(arena, config.title(), secondsLeft, null));
      if (scoreboard == null) {
        scoreboard = manager.createScoreboard(name, title, lines);
        arena.setTabScoreboardName(name);
      } else {
        scoreboard.setTitle(title);
        scoreboard.setLines(lines);
      }
      for (String playerName : arena.playerNames()) {
        TabPlayer tabPlayer = tabPlayer(playerName);
        if (tabPlayer != null) {
          manager.showScoreboard(tabPlayer, scoreboard);
        }
      }
      return true;
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB scoreboard unavailable: " + exception.getMessage());
      return false;
    }
  }

  void clearScoreboard(RuntimeArena arena) {
    try {
      me.neznamy.tab.api.scoreboard.ScoreboardManager manager =
          TabAPI.getInstance().getScoreboardManager();
      if (manager == null) {
        return;
      }
      for (String playerName : arena.playerNames()) {
        TabPlayer tabPlayer = tabPlayer(playerName);
        if (tabPlayer != null && manager.hasCustomScoreboard(tabPlayer)) {
          manager.resetScoreboard(tabPlayer);
        }
      }
      String name = arena.tabScoreboardName();
      if (name != null && manager.getRegisteredScoreboards().containsKey(name)) {
        manager.removeScoreboard(name);
      }
      arena.setTabScoreboardName(null);
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB scoreboard cleanup skipped: " + exception.getMessage());
    }
  }

  // ---- Boss bar ------------------------------------------------------------

  boolean applyBossBar(RuntimeArena arena, String title, double progress,
      org.bukkit.boss.BarColor color, org.bukkit.boss.BarStyle style) {
    try {
      me.neznamy.tab.api.bossbar.BossBarManager manager = TabAPI.getInstance().getBossBarManager();
      if (manager == null) {
        warnTabFeatureOnce("bossbar");
        return false;
      }
      String renderedTitle = LegacyText.legacySection(title);
      float renderedProgress = (float) (clampProgress(progress) * 100.0);
      me.neznamy.tab.api.bossbar.BossBar bossBar = null;
      if (arena.tabBossBarName() != null) {
        bossBar = manager.getBossBar(arena.tabBossBarName());
      }
      if (bossBar == null) {
        bossBar = manager.createBossBar(renderedTitle, renderedProgress,
            TeamPalette.tabBarColor(color), TeamPalette.tabBarStyle(style));
        arena.setTabBossBarName(bossBar.getName());
      } else {
        bossBar.setTitle(renderedTitle);
        bossBar.setProgress(renderedProgress);
        bossBar.setColor(TeamPalette.tabBarColor(color));
        bossBar.setStyle(TeamPalette.tabBarStyle(style));
      }
      for (TabPlayer viewer : List.copyOf(bossBar.getPlayers())) {
        if (!arena.playerNames().contains(viewer.getName())) {
          bossBar.removePlayer(viewer);
        }
      }
      for (String playerName : arena.playerNames()) {
        TabPlayer tabPlayer = tabPlayer(playerName);
        if (tabPlayer != null && !bossBar.containsPlayer(tabPlayer)) {
          bossBar.addPlayer(tabPlayer);
        }
      }
      return true;
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB bossbar unavailable: " + exception.getMessage());
      return false;
    }
  }

  void clearBossBar(RuntimeArena arena) {
    try {
      me.neznamy.tab.api.bossbar.BossBarManager manager = TabAPI.getInstance().getBossBarManager();
      String name = arena.tabBossBarName();
      if (manager != null && name != null && manager.getBossBar(name) != null) {
        manager.removeBossBar(name);
      }
      arena.setTabBossBarName(null);
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB bossbar cleanup skipped: " + exception.getMessage());
    }
  }

  // ---- Tablist layout ------------------------------------------------------

  void applyLayout(RuntimeArena arena) {
    try {
      TabAPI api = TabAPI.getInstance();
      LayoutManager layoutManager = api.getLayoutManager();
      if (layoutManager == null) {
        warnTabFeatureOnce("layout");
        applyHeaderFooter(arena);
        return;
      }
      long revision = arena.nextTabLayoutRevision();
      for (String playerName : arena.playerNames()) {
        TabPlayer tabPlayer = tabPlayer(playerName);
        if (tabPlayer != null) {
          String layoutName = "mgl-layout-" + arena.arenaId() + "-" + revision + "-"
              + playerName.toLowerCase(Locale.ROOT);
          Layout layout = layoutManager.createNewLayout(layoutName, TAB_LAYOUT_SIZE);
          fillBackground(layout);
          fillTeamColumns(layout, arena);
          fillRankingColumn(layout, arena);
          fillPersonalColumn(layout, arena, playerName);
          layoutManager.sendLayout(tabPlayer, layout);
        }
      }
      applyHeaderFooter(arena);
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB layout unavailable: " + exception.getMessage());
    }
  }

  private void fillBackground(Layout layout) {
    for (int slot = 1; slot <= TAB_LAYOUT_SIZE; slot++) {
      addSlot(layout, slot, "&8" + " ".repeat(TAB_COLUMN_WIDTH));
    }
  }

  private void fillTeamColumns(Layout layout, RuntimeArena arena) {
    List<ArenaTeamColor> colors = tabTeamColors(arena);
    for (int index = 0; index < colors.size(); index++) {
      int column = index / 4;
      if (column > 1) {
        break;
      }
      int row = index % 4;
      int baseSlot = column * 20 + row * 5 + 1;
      ArenaTeamColor color = colors.get(index);
      ArenaTeam team = teamByColor(arena, color).orElse(new ArenaTeam(color, List.of()));
      long alive = team.playerNames().stream().filter(name -> !arena.isFailed(name)).count();
      boolean teamDown = team.playerNames().size() > 0 && alive == 0;
      String header = (teamDown ? "&m" : TeamPalette.legacyCode(color))
          + "▎ " + TeamPalette.legacyCode(color) + "&l" + TeamPalette.displayName(color)
          + " &8[" + (teamDown ? "&c" : "&a") + alive + "&8/&7" + team.playerNames().size() + "&8]";
      addSlot(layout, baseSlot, header);
      for (int offset = 0; offset < 4; offset++) {
        String playerName =
            offset < team.playerNames().size() ? team.playerNames().get(offset) : "";
        if (playerName.isBlank()) {
          addSlot(layout, baseSlot + offset + 1, "&8 ");
        } else {
          boolean dead = arena.isFailed(playerName);
          String marker = dead ? "&8✘ &7&m" : "&7● " + TeamPalette.legacyCode(color);
          addSlot(layout, baseSlot + offset + 1, marker + playerName);
        }
      }
    }
  }

  private void fillRankingColumn(Layout layout, RuntimeArena arena) {
    addSlot(layout, 41, "&6&l⚔ 队伍排名");
    long now = System.currentTimeMillis();
    List<ArenaTeam> rankedTeams = arena.teams().stream()
        .sorted(Comparator.comparingInt((ArenaTeam team) -> arena.teamScore(team, now))
            .reversed().thenComparing(team -> team.color().ordinal()))
        .toList();
    int slot = 42;
    int rank = 1;
    for (ArenaTeam team : rankedTeams) {
      if (slot > 60) {
        break;
      }
      ArenaTeamColor color = team.color();
      String medal = switch (rank) {
        case 1 -> "&e①";
        case 2 -> "&7②";
        case 3 -> "&6③";
        default -> "&8" + rank;
      };
      addSlot(layout, slot++, medal + " " + TeamPalette.legacyCode(color) + "&l"
          + TeamPalette.displayName(color) + " &8» &e" + arena.teamScore(team, now) + "&7分");
      if (slot <= 60) {
        addSlot(layout, slot++,
            "  &7⚔ &c" + arena.teamKills(team) + " &8· &7⌛ &a" + arena.teamSurvivalSeconds(team, now)
                + "&7s");
      }
      rank++;
    }
  }

  private void fillPersonalColumn(Layout layout, RuntimeArena arena, String playerName) {
    ArenaPlayerStats stats = arena.playerStats().stream()
        .filter(s -> s.playerName().equalsIgnoreCase(playerName)).findFirst().orElse(null);
    ArenaTeamColor color = stats == null ? null : stats.teamColor();
    String colorCode = color == null ? "&f" : TeamPalette.legacyCode(color);
    String teamName = color == null ? "无队伍" : TeamPalette.displayName(color);
    boolean failed = stats != null && stats.failed();
    addSlot(layout, 61, "&b&l✦ 个人统计");
    addSlot(layout, 62, "&7玩家 " + colorCode + playerName);
    addSlot(layout, 63, "&7队伍 " + colorCode + teamName);
    addSlot(layout, 64, "&7击杀 &c" + (stats == null ? 0 : stats.kills()));
    addSlot(layout, 65, "&7死亡 &f" + (stats == null ? 0 : stats.deaths()));
    addSlot(layout, 66, "&7状态 " + (failed ? "&c☠ 淘汰" : "&a❤ 存活"));
  }

  private void addSlot(Layout layout, int slot, String text) {
    layout.addFixedSlot(slot, widen(text), 1);
  }

  private String widen(String text) {
    String safeText = text == null ? "" : text;
    int visibleLength = TextRender.visibleLength(safeText);
    int padding = Math.max(2, TAB_COLUMN_WIDTH - visibleLength);
    return safeText + "&0" + " ".repeat(padding);
  }

  // ---- Lobby reset ---------------------------------------------------------

  void resetLayout(RuntimeArena arena) {
    try {
      TabAPI api = TabAPI.getInstance();
      LayoutManager layoutManager = api.getLayoutManager();
      HeaderFooterManager headerFooterManager = api.getHeaderFooterManager();
      for (String playerName : arena.playerNames()) {
        TabPlayer tabPlayer = tabPlayer(playerName);
        if (tabPlayer == null) {
          continue;
        }
        sendLobbyLayout(layoutManager, tabPlayer);
        if (headerFooterManager != null) {
          headerFooterManager.setHeaderAndFooter(tabPlayer, "", "");
        }
      }
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB layout cleanup skipped: " + exception.getMessage());
    }
  }

  void resetView(Player player) {
    try {
      TabAPI api = TabAPI.getInstance();
      TabPlayer tabPlayer = tabPlayer(player.getName());
      if (tabPlayer == null) {
        return;
      }
      sendLobbyLayout(api.getLayoutManager(), tabPlayer);
      HeaderFooterManager headerFooterManager = api.getHeaderFooterManager();
      if (headerFooterManager != null) {
        headerFooterManager.setHeaderAndFooter(tabPlayer, "", "");
      }
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB reset skipped: " + exception.getMessage());
    }
  }

  void resetLobbyTabViews() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (registry.isInActiveArena(player.getName())) {
        continue;
      }
      resetView(player);
    }
  }

  private void sendLobbyLayout(LayoutManager layoutManager, TabPlayer tabPlayer) {
    if (layoutManager == null || tabPlayer == null) {
      return;
    }
    List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
    onlinePlayers.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
    int layoutSize = Math.max(1, Math.min(TAB_LAYOUT_SIZE, onlinePlayers.size()));
    Layout layout = layoutManager.createNewLayout(
        "mgl-lobby-" + tabPlayer.getUniqueId() + "-" + System.nanoTime(), layoutSize);
    int slot = 1;
    for (Player onlinePlayer : onlinePlayers) {
      if (slot > layoutSize) {
        break;
      }
      layout.addFixedSlot(slot++, onlinePlayer.getName(), 1);
    }
    layoutManager.sendLayout(tabPlayer, layout);
  }

  private void applyHeaderFooter(RuntimeArena arena) {
    try {
      HeaderFooterManager manager = TabAPI.getInstance().getHeaderFooterManager();
      if (manager == null) {
        warnTabFeatureOnce("header-footer");
        return;
      }
      String name = gameNameSupplier.get();
      long alive = arena.aliveTeamCount();
      String header = "§r\n§b§l" + name + " §8» §f" + arena.playerNames().size()
          + " §7玩家 §8· §a" + alive + " §7存活队伍\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";
      String footer = "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7由 §a§lMinigameLib §7驱动§r";
      for (String playerName : arena.playerNames()) {
        TabPlayer tabPlayer = tabPlayer(playerName);
        if (tabPlayer != null) {
          manager.setHeaderAndFooter(tabPlayer, header, footer);
        }
      }
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB header unavailable: " + exception.getMessage());
    }
  }

  // ---- helpers -------------------------------------------------------------

  private List<ArenaTeamColor> tabTeamColors(RuntimeArena arena) {
    List<ArenaTeamColor> configured =
        arena.layout().teamSpawns().stream().map(ArenaTeamSpawn::color).distinct().toList();
    if (!configured.isEmpty()) {
      return configured;
    }
    return arena.teams().stream().map(ArenaTeam::color).distinct().toList();
  }

  private java.util.Optional<ArenaTeam> teamByColor(RuntimeArena arena, ArenaTeamColor color) {
    return arena.teams().stream().filter(team -> team.color() == color).findFirst();
  }

  private TabPlayer tabPlayer(String playerName) {
    try {
      TabPlayer player = TabAPI.getInstance().getPlayer(playerName);
      return player != null && player.isLoaded() ? player : null;
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private void warnTabFeatureOnce(String feature) {
    if (warnedTabFeatures.add(feature)) {
      plugin.getLogger().warning(
          "TAB " + feature + " manager 不可用，请确认 plugins/TAB/config.yml 中对应功能已启用并已 /tab reload。");
    }
  }

  private static double clampProgress(double progress) {
    return Math.max(0.0, Math.min(1.0, progress));
  }

  private static String resolveGameName() {
    org.bukkit.plugin.Plugin skyBattle = Bukkit.getPluginManager().getPlugin("SkyBattle");
    return skyBattle == null ? "SkyBattle" : skyBattle.getPluginMeta().getName();
  }

  /** The display name of the consuming game plugin, used in headers and end-of-game messages. */
  String gameName() {
    return gameNameSupplier.get();
  }

  void shutdown() {
    lobbyRefreshTask.cancel();
  }
}
