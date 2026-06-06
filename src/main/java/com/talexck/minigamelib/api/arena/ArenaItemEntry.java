package com.talexck.minigamelib.api.arena;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import net.kyori.adventure.text.Component;

import java.util.Objects;

public record ArenaItemEntry(
    String name,
    ItemStack item,
    int number,
    ArenaItemMode mode,
    ArenaPotionItemConfig potionConfig,
    boolean igniteTntOnPlace,
    boolean splitInLoot) {

  public ArenaItemEntry(String name, ItemStack item, int number, ArenaItemMode mode,
      ArenaPotionItemConfig potionConfig) {
    this(name, item, number, mode, potionConfig, false, false);
  }

  public ArenaItemEntry(String name, ItemStack item, int number, ArenaItemMode mode,
      ArenaPotionItemConfig potionConfig, boolean igniteTntOnPlace) {
    this(name, item, number, mode, potionConfig, igniteTntOnPlace, false);
  }

  public ArenaItemEntry {
    name = name == null ? "" : name;
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

  @SuppressWarnings("deprecation")
  public ItemStack createStack() {
    ItemStack stack = item.clone();
    stack.setAmount(number);
    if (!name.isBlank() || hasCustomPotionVisual()) {
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
        if (!name.isBlank()) {
          meta.displayName(Component.text(name));
        }
        if (potionConfig != null && potionConfig.projectileCustomModelData() > 0) {
          meta.setCustomModelData(potionConfig.projectileCustomModelData());
        }
        applyItemModel(meta);
        stack.setItemMeta(meta);
      }
    }
    return stack;
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
