package com.talexck.minigamelib.core.arena;

import com.talexck.minigamelib.api.arena.ArenaActionBarConfig;
import com.talexck.minigamelib.api.arena.ArenaBossBarConfig;
import com.talexck.minigamelib.api.arena.ArenaScoreboardConfig;
import com.talexck.minigamelib.api.arena.ArenaSound;
import com.talexck.minigamelib.api.arena.ArenaSoundConfig;
import com.talexck.minigamelib.api.arena.ArenaStatus;
import com.talexck.minigamelib.api.arena.ArenaStopReason;
import com.talexck.minigamelib.api.arena.ArenaTeam;
import com.talexck.minigamelib.api.arena.ArenaTeamColor;
import com.talexck.minigamelib.api.arena.ArenaTitleConfig;
import com.talexck.minigamelib.api.arena.ArenaTitleFrame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Player-facing presentation for an arena: scoreboards, boss bar, titles, action bars, chat
 * broadcasts and the end-of-game ranking. Delegates to {@link TabDisplayService} when the TAB
 * plugin is available, falling back to vanilla Bukkit otherwise. Also owns placeholder rendering.
 */
final class DisplayService implements ArenaDisplay, ArenaTextRenderer {

  private final JavaPlugin plugin;
  private final ArenaRegistry registry;
  private final TabDisplayService tab;

  DisplayService(JavaPlugin plugin, ArenaRegistry registry, TabDisplayService tab) {
    this.plugin = plugin;
    this.registry = registry;
    this.tab = tab;
  }

  // ---- Scoreboard ----------------------------------------------------------

  void applyScoreboards(RuntimeArena arena, int secondsLeft) {
    tab.applyLayout(arena);
    if (tab.applyScoreboard(arena, secondsLeft, this)) {
      return;
    }
    ArenaScoreboardConfig config = arena.settings().scoreboard();
    if (!config.enabled()) {
      return;
    }
    ScoreboardManager manager = Bukkit.getScoreboardManager();
    if (manager == null) {
      return;
    }
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null) {
        continue;
      }
      Scoreboard scoreboard = manager.getNewScoreboard();
      Component displayName =
          LegacyText.component(render(arena, config.title(), secondsLeft, null, playerName));
      Objective objective = scoreboard.registerNewObjective("arena", Criteria.DUMMY, displayName);
      objective.setDisplaySlot(DisplaySlot.SIDEBAR);

      List<String> lines = config.lines();
      for (int index = 0; index < lines.size(); index++) {
        String line = uniqueScoreboardLine(
            LegacyText.legacySection(render(arena, lines.get(index), secondsLeft, null, playerName)),
            index);
        objective.getScore(line).setScore(lines.size() - index);
      }
      player.setScoreboard(scoreboard);
    }
  }

  @Override
  public void refreshScoreboards(RuntimeArena arena, int secondsLeft) {
    applyScoreboards(arena, secondsLeft);
  }

  void clearScoreboards(RuntimeArena arena) {
    tab.clearScoreboard(arena);
    tab.resetLayout(arena);
    ScoreboardManager manager = Bukkit.getScoreboardManager();
    if (manager == null) {
      return;
    }
    Scoreboard mainScoreboard = manager.getMainScoreboard();
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
    if (tab.applyBossBar(arena, render(arena, config.title(), secondsLeft, null),
        resolveBossBarProgress(arena, secondsLeft), config.color(), config.style())) {
      return;
    }
    BossBar bossBar = arena.bossBar();
    if (bossBar == null) {
      bossBar = Bukkit.createBossBar(
          LegacyText.legacySection(render(arena, config.title(), secondsLeft, null)), config.color(),
          config.style());
      arena.setBossBar(bossBar);
    }
    bossBar.setTitle(LegacyText.legacySection(render(arena, config.title(), secondsLeft, null)));
    bossBar.setColor(config.color());
    bossBar.setStyle(config.style());
    bossBar.setProgress(resolveBossBarProgress(arena, secondsLeft));
    bossBar.removeAll();
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
        bossBar.addPlayer(player);
      }
    }
  }

  void updateBossBar(RuntimeArena arena, String title, double progress) {
    ArenaBossBarConfig config = arena.settings().bossBar();
    if (tab.applyBossBar(arena, render(arena, title, 0, null), progress, config.color(),
        config.style())) {
      return;
    }
    BossBar bossBar = arena.bossBar();
    if (bossBar == null) {
      bossBar = Bukkit.createBossBar(LegacyText.legacySection(render(arena, title, 0, null)),
          config.color(), config.style());
      arena.setBossBar(bossBar);
    }
    bossBar.setTitle(LegacyText.legacySection(render(arena, title, 0, null)));
    bossBar.setProgress(clampProgress(progress));
    bossBar.removeAll();
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player != null) {
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
        player.sendMessage(LegacyText.component(render(arena, message, secondsLeft, reason,
            playerName)));
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

  void sendTitle(RuntimeArena arena, ArenaTitleFrame frame, int secondsLeft, ArenaStopReason reason) {
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

  void sendCountdownTitle(RuntimeArena arena, int secondsLeft) {
    // Hypixel-style emphasised countdown: large number with a pulsing colour for the final ticks.
    String color = secondsLeft <= 3 ? "&c&l" : secondsLeft <= 5 ? "&e&l" : "&a&l";
    ArenaTitleFrame frame = new ArenaTitleFrame(color + "%seconds%", "&7游戏即将开始",
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
    org.bukkit.Sound sound = secondsLeft <= 0 ? org.bukkit.Sound.ENTITY_PLAYER_LEVELUP
        : org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING;
    float pitch = secondsLeft <= 0 ? 1.1f : 1.75f;
    playSound(arena, ArenaSound.minecraft(sound, 0.8f, pitch));
  }

  // ---- End-of-game ranking -------------------------------------------------

  void broadcastFinalTeamRanking(RuntimeArena arena, String gameTitle) {
    long now = System.currentTimeMillis();
    List<ArenaTeamColor> ranking = arena.finalTeamRanking();
    String bar = "&8&m                                        ";
    for (String playerName : arena.playerNames()) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null) {
        continue;
      }
      player.sendMessage(LegacyText.component(bar));
      player.sendMessage(LegacyText.component("&b&l" + gameTitle + " &r&7- 最终排名"));
      player.sendMessage(LegacyText.component(""));
      for (int index = 0; index < ranking.size(); index++) {
        ArenaTeamColor color = ranking.get(index);
        ArenaTeam team = teamByColor(arena, color).orElse(new ArenaTeam(color, List.of()));
        String medal = switch (index) {
          case 0 -> "&e&l① ";
          case 1 -> "&7&l② ";
          case 2 -> "&6&l③ ";
          default -> "&8&l" + (index + 1) + " ";
        };
        String line = medal + TeamPalette.legacyCode(color) + "&l" + TeamPalette.displayName(color)
            + " &8» " + finalRankingPlayers(team, color)
            + " &8| &e" + arena.teamScore(team, now) + "&7分 &8· &c" + arena.teamKills(team)
            + "&7杀 &8· &a" + arena.teamSurvivalSeconds(team, now) + "&7s";
        player.sendMessage(LegacyText.component(line));
      }
      player.sendMessage(LegacyText.component(bar));
    }
  }

  private String finalRankingPlayers(ArenaTeam team, ArenaTeamColor color) {
    if (team.playerNames().isEmpty()) {
      return "&7无玩家";
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
    String team = playerName == null ? "" : arena.teamOf(playerName).map(Enum::name).orElse("");
    String kills = playerName == null ? "0"
        : arena.playerStats().stream().filter(stats -> stats.playerName().equals(playerName))
            .findFirst().map(stats -> Integer.toString(stats.kills())).orElse("0");
    String deaths = playerName == null ? "0"
        : arena.playerStats().stream().filter(stats -> stats.playerName().equals(playerName))
            .findFirst().map(stats -> Integer.toString(stats.deaths())).orElse("0");
    String countdown = Integer.toString(Math.max(0, secondsLeft));
    Map<String, String> placeholders = new LinkedHashMap<>();
    placeholders.put("{arena}", arena.arenaId());
    placeholders.put("{template}", arena.templateId());
    placeholders.put("{world}", arena.world().runtimeWorldName());
    placeholders.put("{status}", arena.status().name());
    placeholders.put("{players}", Integer.toString(arena.playerNames().size()));
    placeholders.put("{aliveTeams}", Long.toString(arena.teams().stream()
        .filter(teamValue -> !arena.isTeamFailed(teamValue.color())).count()));
    placeholders.put("{winner}", arena.winningTeam() == null ? ""
        : TeamPalette.displayName(arena.winningTeam()));
    placeholders.put("{team}", team);
    placeholders.put("{kills}", kills);
    placeholders.put("{deaths}", deaths);
    placeholders.put("{countdown}", countdown);
    placeholders.put("%seconds%", countdown);
    placeholders.put("{reason}", reason == null ? "" : reason.name());
    return TextRender.render(text, placeholders);
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
    return clampProgress(config.runningProgress());
  }

  private static double clampProgress(double progress) {
    return Math.max(0.0, Math.min(1.0, progress));
  }

  private java.util.Optional<ArenaTeam> teamByColor(RuntimeArena arena, ArenaTeamColor color) {
    return arena.teams().stream().filter(team -> team.color() == color).findFirst();
  }
}
