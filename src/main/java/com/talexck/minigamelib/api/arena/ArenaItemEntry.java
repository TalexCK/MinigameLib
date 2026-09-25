package com.talexck.minigamelib.api.arena;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Objects;

/**
 * An item handed out by an arena (beginning kit or loot).
 *
 * <p>{@code name} and {@code lore} accept legacy {@code &} colour codes. Special item modes are
 * matched by the plain text of their name, so colours never break matching.
 */
public record ArenaItemEntry(
    String name,
    ItemStack item,
    int number,
    ArenaItemMode mode,
    ArenaPotionItemConfig potionConfig,
    boolean igniteTntOnPlace,
    boolean splitInLoot,
    List<String> lore) {

  public ArenaItemEntry(String name, ItemStack item, int number, ArenaItemMode mode,
      ArenaPotionItemConfig potionConfig) {
    this(name, item, number, mode, potionConfig, false, false, List.of());
  }

  public ArenaItemEntry(String name, ItemStack item, int number, ArenaItemMode mode,
      ArenaPotionItemConfig potionConfig, boolean igniteTntOnPlace) {
    this(name, item, number, mode, potionConfig, igniteTntOnPlace, false, List.of());
  }

  public ArenaItemEntry(String name, ItemStack item, int number, ArenaItemMode mode,
      ArenaPotionItemConfig potionConfig, boolean igniteTntOnPlace, boolean splitInLoot) {
    this(name, item, number, mode, potionConfig, igniteTntOnPlace, splitInLoot, List.of());
  }

  public ArenaItemEntry {
    name = name == null ? "" : name;
    lore = lore == null ? List.of() : List.copyOf(lore);
    Objects.requireNonNull(item, "item");
    if (number <= 0) {
      throw new IllegalArgumentException("number must be positive");
    }
    if (mode == null) {
      mode = ArenaItemMode.DEFAULT;
    }
    if ((mode == ArenaItemMode.POTION || mode == ArenaItemMode.SELF_POTION)
        && potionConfig == null) {
      throw new IllegalArgumentException("potionConfig is required for potion item modes");
    }
    item = item.clone();
  }

  /** Returns a copy of this entry with a different amount. */
  public ArenaItemEntry withNumber(int number) {
    return new ArenaItemEntry(name, item, number, mode, potionConfig, igniteTntOnPlace,
        splitInLoot, lore);
  }

  /** Returns a copy of this entry with the given lore lines. */
  public ArenaItemEntry withLore(List<String> lore) {
    return new ArenaItemEntry(name, item, number, mode, potionConfig, igniteTntOnPlace,
        splitInLoot, lore);
  }

  /** The name without colour codes, used to recognise special items in inventories. */
  public String plainName() {
    return PlainTextComponentSerializer.plainText().serialize(styled(name));
  }

  @SuppressWarnings("deprecation")
  public ItemStack createStack() {
    ItemStack stack = item.clone();
    stack.setAmount(Math.min(number, Math.max(1, stack.getMaxStackSize())));
    if (name.isBlank() && lore.isEmpty() && !hasCustomPotionVisual()) {
      return stack;
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) {
      return stack;
    }
    if (!name.isBlank()) {
      meta.displayName(styled(name));
    }
    if (!lore.isEmpty()) {
      meta.lore(lore.stream().map(ArenaItemEntry::styled).toList());
    }
    if (potionConfig != null && potionConfig.projectileCustomModelData() > 0) {
      meta.setCustomModelData(potionConfig.projectileCustomModelData());
    }
    applyItemModel(meta);
    stack.setItemMeta(meta);
    return stack;
  }

  private static Component styled(String text) {
    return LegacyComponentSerializer.legacyAmpersand().deserialize(text)
        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
  }

  private boolean hasCustomPotionVisual() {
    return potionConfig != null
        && (potionConfig.projectileCustomModelData() > 0 || !potionConfig.itemModelKey().isBlank());
  }

  private void applyItemModel(ItemMeta meta) {
    if (potionConfig == null || potionConfig.itemModelKey().isBlank()) {
      return;
    }
    NamespacedKey key = NamespacedKey.fromString(potionConfig.itemModelKey());
    if (key != null) {
      meta.setItemModel(key);
    }
  }
}
