package com.talexck.minigamelib.core.arena;

import java.util.Map;

/** Pure text helpers for placeholder substitution and TAB column width, extracted for testing. */
final class TextRender {

  private TextRender() {
  }

  /**
   * Replaces each {@code key} occurrence in {@code text} with its mapped value. Keys are expected to
   * already include their delimiters (e.g. {@code "{arena}"} or {@code "%seconds%"}).
   */
  static String render(String text, Map<String, String> placeholders) {
    String result = text;
    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
      result = result.replace(entry.getKey(), entry.getValue());
    }
    return result;
  }

  /**
   * Computes the visible (rendered) length of a legacy-coloured string, skipping {@code &x} and
   * {@code §x} colour codes and counting non-ASCII (e.g. CJK) characters as width 2.
   */
  static int visibleLength(String text) {
    int length = 0;
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      if ((character == '&' || character == '§') && index + 1 < text.length()) {
        index++;
        continue;
      }
      length += character > 0x7F ? 2 : 1;
    }
    return length;
  }
}
