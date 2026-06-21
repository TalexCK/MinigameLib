package com.talexck.minigamelib.core.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TextRenderTest {

  @Test
  void substitutesAllPlaceholders() {
    Map<String, String> placeholders = new LinkedHashMap<>();
    placeholders.put("{arena}", "arena-1");
    placeholders.put("{players}", "8");
    assertEquals("arena-1 has 8 players",
        TextRender.render("{arena} has {players} players", placeholders));
  }

  @Test
  void leavesUnknownPlaceholdersUntouched() {
    assertEquals("{unknown}", TextRender.render("{unknown}", Map.of("{arena}", "x")));
  }

  @Test
  void handlesPercentStyleAndRepeatedPlaceholders() {
    Map<String, String> placeholders = new LinkedHashMap<>();
    placeholders.put("%seconds%", "3");
    assertEquals("3...3", TextRender.render("%seconds%...%seconds%", placeholders));
  }

  @Test
  void visibleLengthSkipsAmpersandColorCodes() {
    assertEquals(5, TextRender.visibleLength("&aHello"));
  }

  @Test
  void visibleLengthSkipsSectionColorCodes() {
    assertEquals(5, TextRender.visibleLength("§cWorld"));
  }

  @Test
  void visibleLengthCountsCjkAsWidthTwo() {
    // "红队" = 2 CJK chars -> width 4.
    assertEquals(4, TextRender.visibleLength("红队"));
  }

  @Test
  void visibleLengthCombinesColorCodesAndCjk() {
    // "&c红队" -> color code skipped, 2 CJK chars -> 4.
    assertEquals(4, TextRender.visibleLength("&c红队"));
  }

  @Test
  void trailingColorAmpersandCountsAsVisibleWhenNoFollowingChar() {
    // A lone trailing '&' has no following code char, so it counts as 1 visible char.
    assertEquals(1, TextRender.visibleLength("&"));
  }
}
