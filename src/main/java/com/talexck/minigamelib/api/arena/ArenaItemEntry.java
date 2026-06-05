package com.talexck.minigamelib.api.arena;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;

import java.util.Objects;

public record ArenaItemEntry(
    String name,
    ItemStack item,
    int number,
    ArenaItemMode mode,
    ArenaPotionItemConfig potionConfig) {

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

  public ItemStack createStack() {
    ItemStack stack = item.clone();
    stack.setAmount(number);
    if (!name.isBlank()) {
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
        meta.displayName(Component.text(name));
        stack.setItemMeta(meta);
      }
    }
    return stack;
  }
}
