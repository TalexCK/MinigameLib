package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaBossBarConfig;
import com.talexck.minigamelib.api.arena.ArenaPresentation;
import com.talexck.minigamelib.api.arena.ArenaScoreboardConfig;
import com.talexck.minigamelib.api.arena.ArenaSound;
import com.talexck.minigamelib.api.arena.ArenaSoundConfig;
import com.talexck.minigamelib.api.arena.ArenaStatus;
import com.talexck.minigamelib.api.arena.ArenaStopReason;
import com.talexck.minigamelib.api.arena.ArenaTeam;
import com.talexck.minigamelib.api.arena.ArenaTeamColor;
import com.talexck.minigamelib.api.arena.ArenaTitleFrame;
import com.talexck.minigamelib.core.lang.LanguageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Player-facing presentation for an arena: scoreboards, boss bar, titles, action bars, chat
 * broadcasts and the end-of-game ranking. Delegates to {@link TabDisplayService} when the TAB
 * plugin is available, falling back to vanilla Bukkit otherwise. Also owns placeholder rendering.
 */
final class DisplayService implements ArenaTextRenderer {

  private static final long LAYOUT_REFRESH_INTERVAL_MILLIS = 2_000L;

  private final TabDisplayService tab;
  private final LanguageService language;
  private Function<RuntimeArena, String> boundaryStatus = arena -> "";

  DisplayService(TabDisplayService tab, LanguageService language) {
    this.tab = tab;
    this.language = language;
  }

  void setBoundaryStatus(Function<RuntimeArena, String> boundaryStatus) {
    this.boundaryStatus = boundaryStatus;
  }

  String text(String key) {
    return language.text(key);
  }

  // ---- Scoreboard ----------------------------------------------------------

  /** Redraws sidebar and tablist immediately (used on state changes such as deaths). */
  void applyScoreboards(RuntimeArena arena, int secondsLeft) {
    arena.setLastLayoutRefreshMillis(System.currentTimeMillis());
    tab.applyLayout(arena, this);
    applySidebar(arena, secondsLeft);
  }

  /** Periodic refresh: the sidebar every call, the heavier tablist layout at most every 2s. */
  void tickScoreboards(RuntimeArena arena, int secondsLeft) {
    long now = System.currentTimeMillis();
    if (now - arena.lastLayoutRefreshMillis() >= LAYOUT_REFRESH_INTERVAL_MILLIS) {
      arena.setLastLayoutRefreshMillis(now);
      tab.applyLayout(arena, this);
    }
    applySidebar(arena, secondsLeft);
  }

  private void applySidebar(RuntimeArena arena, int secondsLeft) {
    if (tab.applyScoreboard(arena, secondsLeft, this)) {
      return;
    }
    ArenaScoreboardConfig config = arena.settings().scoreboard();
    if (!config.enabled()) {
      return;
    }
    ScoreboardManager manager = Bukkit.getScoreboardManager();
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null) {
        continue;
      }
      Scoreboard scoreboard = player.getScoreboard();
      if (scoreboard == manager.getMainScoreboard()) {
        scoreboard = manager.getNewScoreboard();
      }
      Objective old = scoreboard.getObjective("arena");
      if (old != null) {
        old.unregister();
      }
      Component displayName =
          LegacyText.component(render(arena, config.title(), secondsLeft, null, playerName));
      Objective objective = scoreboard.registerNewObjective("arena", Criteria.DUMMY, displayName);
      objective.setDisplaySlot(DisplaySlot.SIDEBAR);
      List<String> lines = config.lines();
      for (int index = 0; index < lines.size(); index++) {
        String line = uniqueScoreboardLine(LegacyText
            .legacySection(render(arena, lines.get(index), secondsLeft, null, playerName)), index);
        objective.getScore(line).setScore(lines.size() - index);
      }
      if (player.getScoreboard() != scoreboard) {
        player.setScoreboard(scoreboard);
      }
    }
  }

  void refreshScoreboards(RuntimeArena arena, int secondsLeft) {
    applyScoreboards(arena, secondsLeft);
  }

  void clearScoreboards(RuntimeArena arena) {
    tab.clearScoreboard(arena);
    tab.resetLayout(arena);
    Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        player.setScoreboard(mainScoreboard);
      }
    }
  }

  // ---- Boss bar ------------------------------------------------------------

  void applyBossBar(RuntimeArena arena, int secondsLeft) {
    ArenaBossBarConfig config = arena.settings().bossBar();
    if (!config.enabled()) {
      return;
    }
    String title = config.title();
    double progress = resolveBossBarProgress(arena, secondsLeft);
    String running = arena.settings().presentation().runningBossBar();
    if (arena.status() == ArenaStatus.RUNNING && !running.isBlank()) {
      title = running;
    }
    showBossBar(arena, render(arena, title, secondsLeft, null), progress);
  }

  void updateBossBar(RuntimeArena arena, String title, double progress) {
    showBossBar(arena, render(arena, title, 0, null), progress);
  }

  private void showBossBar(RuntimeArena arena, String renderedTitle, double progress) {
    ArenaBossBarConfig config = arena.settings().bossBar();
    if (tab.applyBossBar(arena, renderedTitle, progress, config.color(), config.style())) {
      return;
    }
    BossBar bossBar = arena.bossBar();
    if (bossBar == null) {
      bossBar = Bukkit.createBossBar(LegacyText.legacySection(renderedTitle), config.color(),
          config.style());
      arena.setBossBar(bossBar);
    }
    bossBar.setTitle(LegacyText.legacySection(renderedTitle));
    bossBar.setColor(config.color());
    bossBar.setStyle(config.style());
    bossBar.setProgress(clampProgress(progress));
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null && !bossBar.getPlayers().contains(player)) {
        bossBar.addPlayer(player);
      }
    }
  }

  void clearBossBar(RuntimeArena arena) {
    tab.clearBossBar(arena);
    BossBar bossBar = arena.bossBar();
    if (bossBar != null) {
      bossBar.removeAll();
      arena.setBossBar(null);
    }
  }

  // ---- Chat / action bar / title ------------------------------------------

  void broadcastMessages(RuntimeArena arena, List<String> messages, int secondsLeft,
      ArenaStopReason reason) {
    for (String message : messages) {
      broadcastMessage(arena, message, secondsLeft, reason);
    }
  }

  void broadcastMessage(RuntimeArena arena, String message, int secondsLeft,
      ArenaStopReason reason) {
    if (message == null || message.isBlank()) {
      return;
    }
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        player.sendMessage(
            LegacyText.component(render(arena, message, secondsLeft, reason, playerName)));
      }
    }
  }

  void sendConfiguredActionBar(RuntimeArena arena, String message, int secondsLeft,
      ArenaStopReason reason) {
    if (arena.settings().actionBar().enabled()) {
      sendActionBar(arena, message, secondsLeft, reason);
    }
  }

  void sendActionBar(RuntimeArena arena, String message, int secondsLeft, ArenaStopReason reason) {
    if (message == null || message.isBlank()) {
      return;
    }
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        player.sendActionBar(
            LegacyText.component(render(arena, message, secondsLeft, reason, playerName)));
      }
    }
  }

  void sendConfiguredTitle(RuntimeArena arena, ArenaTitleFrame frame, int secondsLeft,
      ArenaStopReason reason) {
    if (arena.settings().title().enabled()) {
      sendTitle(arena, frame, secondsLeft, reason);
    }
  }

  void sendTitle(RuntimeArena arena, ArenaTitleFrame frame, int secondsLeft,
      ArenaStopReason reason) {
    if (frame == null || (frame.title().isBlank() && frame.subtitle().isBlank())) {
      return;
    }
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        Title rendered = Title.title(
            LegacyText.component(render(arena, frame.title(), secondsLeft, reason, playerName)),
            LegacyText.component(render(arena, frame.subtitle(), secondsLeft, reason, playerName)),
            Title.Times.times(frame.fadeIn(), frame.stay(), frame.fadeOut()));
        player.showTitle(rendered);
      }
    }
  }

  /** Shows a title to one player; {@code extra} holds additional placeholder replacements. */
  void showTitle(Player player, RuntimeArena arena, String title, String subtitle,
      Map<String, String> extra, Duration fadeIn, Duration stay, Duration fadeOut) {
    if (title.isBlank() && subtitle.isBlank()) {
      return;
    }
    player.showTitle(Title.title(
        LegacyText.component(renderExtra(arena, title, player.getName(), extra)),
        LegacyText.component(renderExtra(arena, subtitle, player.getName(), extra)),
        Title.Times.times(fadeIn, stay, fadeOut)));
  }

  void showActionBar(Player player, RuntimeArena arena, String text, Map<String, String> extra) {
    if (text.isBlank()) {
      return;
    }
    player.sendActionBar(LegacyText.component(renderExtra(arena, text, player.getName(), extra)));
  }

  private String renderExtra(RuntimeArena arena, String text, String playerName,
      Map<String, String> extra) {
    String rendered = text;
    for (Map.Entry<String, String> entry : extra.entrySet()) {
      rendered = rendered.replace(entry.getKey(), entry.getValue());
    }
    return render(arena, rendered, 0, null, playerName);
  }

  void sendCountdownTitle(RuntimeArena arena, int secondsLeft) {
    if (secondsLeft > 5) {
      return;
    }
    String color = secondsLeft <= 0 ? "&a&l" : secondsLeft <= 3 ? "&c&l" : "&e&l";
    String title = secondsLeft <= 0 ? language.text("arena.countdown-go") : color + "%seconds%";
    ArenaTitleFrame frame = new ArenaTitleFrame(title, language.text("arena.countdown-subtitle"),
        Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(150));
    sendTitle(arena, frame, secondsLeft, null);
  }

  // ---- Sounds --------------------------------------------------------------

  void playConfiguredSound(RuntimeArena arena, ArenaSound sound) {
    ArenaSoundConfig config = arena.settings().sounds();
    if (config.enabled() && sound != null) {
      playSound(arena, sound);
    }
  }

  void playSound(RuntimeArena arena, ArenaSound sound) {
    if (sound == null) {
      return;
    }
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null) {
        continue;
      }
      if (sound.minecraftSound() != null) {
        player.playSound(player.getLocation(), sound.minecraftSound(), sound.category(),
            sound.volume(), sound.pitch());
      } else {
        player.playSound(player.getLocation(), sound.customSound(), sound.category(),
            sound.volume(), sound.pitch());
      }
    }
  }

  void playCountdownSound(RuntimeArena arena, int secondsLeft) {
    if (secondsLeft > 5) {
      return;
    }
    org.bukkit.Sound sound = secondsLeft <= 0 ? org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL
        : org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT;
    float pitch = secondsLeft <= 0 ? 1.4f : 0.9f + (5 - secondsLeft) * 0.15f;
    playSound(arena, ArenaSound.minecraft(sound, secondsLeft <= 0 ? 0.5f : 1.0f, pitch));
  }

  // ---- End-of-game ranking -------------------------------------------------

  void broadcastFinalTeamRanking(RuntimeArena arena) {
    List<ArenaTeamColor> ranking = arena.finalTeamRanking();
    boolean solo = arena.teams().stream().allMatch(team -> team.playerNames().size() <= 1);
    String bar = "&8&m                                                ";
    String gameName = arena.settings().presentation().gameName();
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null) {
        continue;
      }
      player.sendMessage(LegacyText.component(bar));
      player.sendMessage(LegacyText.component(center(language.text("ranking.title",
          "{game}", gameName, "{mode}", arena.settings().presentation().modeName()))));
      player.sendMessage(Component.empty());
      int shown = Math.min(ranking.size(), 5);
      for (int index = 0; index < shown; index++) {
        ArenaTeamColor color = ranking.get(index);
        ArenaTeam team = teamByColor(arena, color).orElse(new ArenaTeam(color, List.of()));
        String medal = switch (index) {
          case 0 -> "&6&l#1";
          case 1 -> "&f&l#2";
          case 2 -> "&c&l#3";
          default -> "&7#" + (index + 1);
        };
        String who = solo ? finalRankingPlayers(team, color)
            : TeamPalette.legacyCode(color) + "&l" + TeamPalette.displayName(color) + " &8("
                + finalRankingPlayers(team, color) + "&8)";
        player.sendMessage(LegacyText.component(language.text("ranking.line",
            "{rank}", medal, "{who}", who, "{score}", arena.teamScore(team, 0L),
            "{kills}", arena.teamKills(team))));
      }
      player.sendMessage(Component.empty());
      ArenaTeamColor ownTeam = arena.teamOf(playerName).orElse(null);
      player.sendMessage(LegacyText.component(language.text("ranking.personal",
          "{placement}", ownTeam == null ? "-" : Integer.toString(arena.placementOf(ownTeam)),
          "{total}", ranking.size(),
          "{kills}", arena.kills(playerName),
          "{score}", arena.score(playerName))));
      player.sendMessage(LegacyText.component(bar));
    }
  }

  private String center(String text) {
    int padding = Math.max(0, (48 - TextRender.visibleLength(text)) / 2);
    return " ".repeat(padding) + text;
  }

  private String finalRankingPlayers(ArenaTeam team, ArenaTeamColor color) {
    if (team.playerNames().isEmpty()) {
      return language.text("ranking.no-players");
    }
    String colorCode = TeamPalette.legacyCode(color);
    return colorCode + String.join("&7, " + colorCode, team.playerNames());
  }

  void applyPlayerListName(RuntimeArena arena, Player player) {
    NamedTextColor color =
        arena.teamOf(player.getName()).map(TeamPalette::textColor).orElse(NamedTextColor.WHITE);
    player.playerListName(Component.text(player.getName(), color));
  }

  // ---- placeholder rendering ----------------------------------------------

  @Override
  public String render(RuntimeArena arena, String text, int secondsLeft, ArenaStopReason reason) {
    return render(arena, text, secondsLeft, reason, null);
  }

  String render(RuntimeArena arena, String text, int secondsLeft, ArenaStopReason reason,
      String playerName) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    long now = System.currentTimeMillis();
    ArenaPresentation presentation = arena.settings().presentation();
    ArenaTeamColor teamColor = playerName == null ? null : arena.teamOf(playerName).orElse(null);
    String countdown = Integer.toString(Math.max(0, secondsLeft));
    long timeLeft = arena.secondsLeft(now);
    Map<String, String> placeholders = new LinkedHashMap<>();
    placeholders.put("{arena}", arena.arenaId());
    placeholders.put("{template}", arena.templateId());
    placeholders.put("{world}", arena.world().runtimeWorldName());
    placeholders.put("{status}", statusText(arena));
    placeholders.put("{game}", presentation.gameName());
    placeholders.put("{mode}", presentation.modeName());
    placeholders.put("{map}", presentation.mapName().isBlank() ? arena.templateId()
        : presentation.mapName());
    placeholders.put("{players}", Integer.toString(arena.playerNames().size()));
    placeholders.put("{alivePlayers}", Long.toString(arena.alivePlayerCount()));
    placeholders.put("{aliveTeams}", Long.toString(arena.aliveTeamCount()));
    placeholders.put("{teams}", Integer.toString(arena.teams().size()));
    placeholders.put("{winner}",
        arena.winningTeam() == null ? "" : TeamPalette.legacyCode(arena.winningTeam())
            + TeamPalette.displayName(arena.winningTeam()));
    placeholders.put("{teamColor}", teamColor == null ? "&f" : TeamPalette.legacyCode(teamColor));
    placeholders.put("{teamName}", teamColor == null ? "" : TeamPalette.displayName(teamColor));
    placeholders.put("{team}", teamColor == null ? ""
        : TeamPalette.legacyCode(teamColor) + TeamPalette.displayName(teamColor));
    placeholders.put("{teamAlive}", teamColor == null ? "0"
        : Long.toString(arena.alivePlayers(teamColor)));
    placeholders.put("{teamSize}", teamColor == null ? "0" : Integer.toString(
        teamByColor(arena, teamColor).map(team -> team.playerNames().size()).orElse(0)));
    placeholders.put("{teamScore}", teamColor == null ? "0"
        : Integer.toString(arena.teamScore(teamColor)));
    placeholders.put("{player}", playerName == null ? "" : playerName);
    placeholders.put("{kills}", playerName == null ? "0" : Integer.toString(arena.kills(playerName)));
    placeholders.put("{deaths}",
        playerName == null ? "0" : Integer.toString(arena.deaths(playerName)));
    placeholders.put("{score}", playerName == null ? "0" : Integer.toString(arena.score(playerName)));
    placeholders.put("{alive}", playerName == null ? ""
        : arena.isFailed(playerName) ? language.text("tab.status-dead")
            : language.text("tab.status-alive"));
    placeholders.put("{countdown}", countdown);
    placeholders.put("%seconds%", countdown);
    placeholders.put("{time_left}", timeLeft < 0 ? "--:--" : formatTime(timeLeft));
    placeholders.put("{elapsed}", formatTime(arena.elapsedSeconds(now)));
    placeholders.put("{border}", boundaryStatus.apply(arena));
    placeholders.put("{reason}", reason == null ? "" : language.text("reason."
        + reason.name().toLowerCase(java.util.Locale.ROOT)));
    return TextRender.render(text, placeholders);
  }

  private String statusText(RuntimeArena arena) {
    return language.text("status." + arena.status().name().toLowerCase(java.util.Locale.ROOT));
  }

  static String formatTime(long seconds) {
    long safe = Math.max(0L, seconds);
    return String.format(java.util.Locale.ROOT, "%d:%02d", safe / 60L, safe % 60L);
  }

  private String uniqueScoreboardLine(String line, int index) {
    if (line.isBlank()) {
      return scoreboardSuffix(index);
    }
    return line + scoreboardSuffix(index);
  }

  private String scoreboardSuffix(int index) {
    return "§r".repeat(index + 1);
  }

  private double resolveBossBarProgress(RuntimeArena arena, int secondsLeft) {
    ArenaBossBarConfig config = arena.settings().bossBar();
    int countdownSeconds = arena.settings().countdownSeconds();
    if (config.countdownProgress() && arena.status() == ArenaStatus.COUNTDOWN
        && countdownSeconds > 0) {
      return clampProgress((double) secondsLeft / countdownSeconds);
    }
    if (arena.status() == ArenaStatus.RUNNING && arena.settings().rules().hasTimeLimit()) {
      long left = arena.secondsLeft(System.currentTimeMillis());
      double total = arena.settings().rules().timeLimit().toSeconds();
      return clampProgress(total <= 0 ? 1.0 : left / total);
    }
    return clampProgress(config.runningProgress());
  }

  private static double clampProgress(double progress) {
    return Math.max(0.0, Math.min(1.0, progress));
  }

  java.util.Optional<ArenaTeam> teamByColor(RuntimeArena arena, ArenaTeamColor color) {
    return arena.teams().stream().filter(team -> team.color() == color).findFirst();
  }
}
