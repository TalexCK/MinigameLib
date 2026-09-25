package com.talexck.minigamelib.core.arena;

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
import com.talexck.minigamelib.core.lang.LanguageService;

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
  private final LanguageService language;
  private final java.util.Set<String> warnedTabFeatures =
      java.util.concurrent.ConcurrentHashMap.newKeySet();
  private BukkitTask pendingLobbyRefresh;

  TabDisplayService(JavaPlugin plugin, ArenaRegistry registry, LanguageService language) {
    this.plugin = plugin;
    this.registry = registry;
    this.language = language;
  }

  /** Coalesces lobby tablist refreshes (join/quit bursts) into one refresh next tick. */
  void scheduleLobbyRefresh() {
    if (pendingLobbyRefresh != null && !pendingLobbyRefresh.isCancelled()) {
      return;
    }
    pendingLobbyRefresh = Bukkit.getScheduler().runTaskLater(plugin, () -> {
      pendingLobbyRefresh = null;
      resetLobbyTabViews();
    }, 2L);
  }

  // ---- Scoreboard ----------------------------------------------------------

  boolean applyScoreboard(RuntimeArena arena, int secondsLeft, DisplayService renderer) {
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
      for (String playerName : arena.playerNames()) {
        TabPlayer tabPlayer = tabPlayer(playerName);
        if (tabPlayer == null) {
          continue;
        }
        String name = scoreboardName(arena, playerName);
        List<String> lines = config.lines().stream()
            .map(line -> LegacyText.legacySection(
                renderer.render(arena, line, secondsLeft, null, playerName)))
            .toList();
        String title = LegacyText.legacySection(
            renderer.render(arena, config.title(), secondsLeft, null, playerName));
        me.neznamy.tab.api.scoreboard.Scoreboard scoreboard =
            manager.getRegisteredScoreboards().get(name);
        if (scoreboard == null) {
          scoreboard = manager.createScoreboard(name, title, lines);
        } else {
          scoreboard.setTitle(title);
          scoreboard.setLines(lines);
        }
        if (manager.getActiveScoreboard(tabPlayer) != scoreboard) {
          manager.showScoreboard(tabPlayer, scoreboard);
        }
      }
      return true;
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB scoreboard unavailable: " + exception.getMessage());
      return false;
    }
  }

  private String scoreboardName(RuntimeArena arena, String playerName) {
    return "mgl-sb-" + arena.arenaId() + "-" + playerName.toLowerCase(Locale.ROOT);
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
        String name = scoreboardName(arena, playerName);
        if (manager.getRegisteredScoreboards().containsKey(name)) {
          manager.removeScoreboard(name);
        }
      }
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

  /**
   * Sends every arena player a 4x20 layout: columns 1-2 hold the team roster, column 3 the game
   * info and live ranking, column 4 the viewer's personal stats. All captions come from the
   * MinigameLib language file and support arena placeholders.
   */
  void applyLayout(RuntimeArena arena, DisplayService display) {
    try {
      TabAPI api = TabAPI.getInstance();
      LayoutManager layoutManager = api.getLayoutManager();
      if (layoutManager == null) {
        warnTabFeatureOnce("layout");
        applyHeaderFooter(arena, display);
        return;
      }
      long revision = arena.nextTabLayoutRevision();
      List<String> roster = rosterLines(arena);
      List<String> ranking = rankingLines(arena);
      for (String playerName : arena.playerNames()) {
        TabPlayer tabPlayer = tabPlayer(playerName);
        if (tabPlayer == null) {
          continue;
        }
        String layoutName = "mgl-layout-" + arena.arenaId() + "-" + revision + "-"
            + playerName.toLowerCase(Locale.ROOT);
        Layout layout = layoutManager.createNewLayout(layoutName, TAB_LAYOUT_SIZE);
        fillColumn(layout, 1, 40, roster);
        List<String> info = new ArrayList<>();
        for (String line : language.list("tab.info-lines")) {
          info.add(display.render(arena, line, 0, null, playerName));
        }
        info.add("");
        info.addAll(ranking);
        fillColumn(layout, 41, 60, info);
        List<String> personal = new ArrayList<>();
        for (String line : language.list("tab.personal-lines")) {
          personal.add(display.render(arena, line, 0, null, playerName));
        }
        fillColumn(layout, 61, 80, personal);
        layoutManager.sendLayout(tabPlayer, layout);
      }
      applyHeaderFooter(arena, display);
    } catch (RuntimeException exception) {
      plugin.getLogger().fine("TAB layout unavailable: " + exception.getMessage());
    }
  }

  private void fillColumn(Layout layout, int firstSlot, int lastSlot, List<String> lines) {
    int slot = firstSlot;
    for (String line : lines) {
      if (slot > lastSlot) {
        return;
      }
      addSlot(layout, slot++, line);
    }
    while (slot <= lastSlot) {
      addSlot(layout, slot++, "");
    }
  }

  /** Team blocks packed into two 20-row columns; a block never straddles the column break. */
  private List<String> rosterLines(RuntimeArena arena) {
    boolean solo = arena.teams().stream().allMatch(team -> team.playerNames().size() <= 1);
    List<String> lines = new ArrayList<>();
    if (solo) {
      lines.add(language.text("tab.players-title", "{alive}", arena.alivePlayerCount(),
          "{total}", arena.playerNames().size()));
      arena.teams().stream()
          .sorted(Comparator.comparing((ArenaTeam team) -> team.playerNames().stream()
              .allMatch(arena::isFailed)).thenComparing(team -> team.color().ordinal()))
          .forEach(team -> team.playerNames().forEach(name ->
              lines.add(playerLine(arena, team.color(), name, true))));
      return lines;
    }
    for (ArenaTeamColor color : tabTeamColors(arena)) {
      ArenaTeam team = teamByColor(arena, color).orElse(null);
      if (team == null || team.playerNames().isEmpty()) {
        continue;
      }
      int blockSize = team.playerNames().size() + 1;
      int row = lines.size() % 20;
      if (lines.size() < 20 && row + blockSize > 20) {
        while (lines.size() < 20) {
          lines.add("");
        }
      }
      long alive = arena.alivePlayers(color);
      boolean teamDown = alive == 0;
      lines.add((teamDown ? "&8&m" : TeamPalette.legacyCode(color) + "&l")
          + TeamPalette.displayName(color) + "&r " + (teamDown ? "&8" : "&7") + alive + "/"
          + team.playerNames().size() + " &e" + arena.teamScore(team, 0L) + "✦");
      for (String name : team.playerNames()) {
        lines.add(playerLine(arena, color, name, false));
      }
    }
    return lines;
  }

  private String playerLine(RuntimeArena arena, ArenaTeamColor color, String name,
      boolean withScore) {
    boolean dead = arena.isFailed(name);
    String prefix = dead ? "&8✘ &7&m" : "&a● " + TeamPalette.legacyCode(color);
    String suffix = withScore ? " &r&e" + arena.score(name) + "✦" : "";
    if (!dead && arena.kills(name) > 0) {
      suffix = " &r&c⚔" + arena.kills(name) + suffix;
    }
    return prefix + name + suffix;
  }

  private List<String> rankingLines(RuntimeArena arena) {
    List<String> lines = new ArrayList<>();
    lines.add(language.text("tab.ranking-title"));
    long now = System.currentTimeMillis();
    List<ArenaTeam> ranked = arena.teams().stream()
        .filter(team -> !team.playerNames().isEmpty())
        .sorted(Comparator.comparingInt((ArenaTeam team) -> arena.teamScore(team, now)).reversed()
            .thenComparing(team -> team.color().ordinal()))
        .toList();
    boolean solo = arena.teams().stream().allMatch(team -> team.playerNames().size() <= 1);
    int rank = 1;
    for (ArenaTeam team : ranked) {
      if (rank > 8) {
        break;
      }
      String medal = switch (rank) {
        case 1 -> "&6#1";
        case 2 -> "&f#2";
        case 3 -> "&c#3";
        default -> "&7#" + rank;
      };
      String name = solo ? team.playerNames().getFirst() : TeamPalette.displayName(team.color());
      lines.add(medal + " " + TeamPalette.legacyCode(team.color()) + name + " &e"
          + arena.teamScore(team, now) + "✦");
      rank++;
    }
    return lines;
  }

  private void addSlot(Layout layout, int slot, String text) {
    layout.addFixedSlot(slot, LegacyText.legacySection(widen(text)), 1);
  }

  private String widen(String text) {
    String safeText = text == null ? "" : text;
    int visibleLength = TextRender.visibleLength(safeText);
    int padding = Math.max(1, TAB_COLUMN_WIDTH - visibleLength);
    return safeText + "&r" + " ".repeat(padding);
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

  private void applyHeaderFooter(RuntimeArena arena, DisplayService display) {
    try {
      HeaderFooterManager manager = TabAPI.getInstance().getHeaderFooterManager();
      if (manager == null) {
        warnTabFeatureOnce("header-footer");
        return;
      }
      for (String playerName : arena.playerNames()) {
        TabPlayer tabPlayer = tabPlayer(playerName);
        if (tabPlayer != null) {
          String header = LegacyText.legacySection(display.render(arena,
              String.join("\n", language.list("tab.header")), 0, null, playerName));
          String footer = LegacyText.legacySection(display.render(arena,
              String.join("\n", language.list("tab.footer")), 0, null, playerName));
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

  void shutdown() {
    if (pendingLobbyRefresh != null) {
      pendingLobbyRefresh.cancel();
    }
  }
}
