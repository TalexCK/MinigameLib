package com.talexck.minigamelib.api.arena;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

public final class ArenaItemFactory {

  private ArenaItemFactory() {}

  public static ArenaItemEntry item(String name, Material material, int amount) {
    return item(name, material, amount, ArenaItemMode.DEFAULT, List.of(), material == Material.TNT);
  }

  public static ArenaItemEntry item(String name, Material material, int amount,
      ArenaItemMode mode) {
    return item(name, material, amount, mode, List.of(), material == Material.TNT);
  }

  public static ArenaItemEntry infiniteOffhandBlock(String name, Material material, int amount) {
    return item(name, material, amount, ArenaItemMode.INFINITE_OFFHAND);
  }

  public static ArenaItemEntry teamLeatherHelmet(String name) {
    return teamLeatherArmor(name, Material.LEATHER_HELMET);
  }

  public static ArenaItemEntry teamLeatherChestplate(String name) {
    return teamLeatherArmor(name, Material.LEATHER_CHESTPLATE);
  }

  public static ArenaItemEntry teamLeatherLeggings(String name) {
    return teamLeatherArmor(name, Material.LEATHER_LEGGINGS);
  }

  public static ArenaItemEntry teamLeatherBoots(String name) {
    return teamLeatherArmor(name, Material.LEATHER_BOOTS);
  }

  public static ArenaItemEntry teamLeatherArmor(String name, Material material) {
    if (material != Material.LEATHER_HELMET && material != Material.LEATHER_CHESTPLATE
        && material != Material.LEATHER_LEGGINGS && material != Material.LEATHER_BOOTS) {
      throw new IllegalArgumentException("material must be leather armor");
    }
    return item(name, material, 1, ArenaItemMode.TEAM_LEATHER_ARMOR);
  }

  public static ArenaItemEntry item(String name, Material material, int amount,
      List<ArenaItemEnchantment> enchantments) {
    return item(name, material, amount, ArenaItemMode.DEFAULT, enchantments,
        material == Material.TNT);
  }

  public static ArenaItemEntry item(String name, Material material, int amount, ArenaItemMode mode,
      List<ArenaItemEnchantment> enchantments, boolean igniteTntOnPlace) {
    return item(name, material, amount, mode, enchantments, igniteTntOnPlace, false);
  }

  public static ArenaItemEntry item(String name, Material material, int amount, ArenaItemMode mode,
      List<ArenaItemEnchantment> enchantments, boolean igniteTntOnPlace, boolean splitInLoot) {
    return item(name, material, amount, mode, enchantments, igniteTntOnPlace, splitInLoot,
        List.of());
  }

  public static ArenaItemEntry item(String name, Material material, int amount, ArenaItemMode mode,
      List<ArenaItemEnchantment> enchantments, boolean igniteTntOnPlace, boolean splitInLoot,
      List<String> lore) {
    ItemStack stack = new ItemStack(material);
    for (ArenaItemEnchantment enchantment : enchantments) {
      stack.addUnsafeEnchantment(resolveEnchantment(enchantment.key()), enchantment.level());
    }
    return new ArenaItemEntry(name, stack, amount, mode, null, igniteTntOnPlace, splitInLoot,
        lore);
  }

  /** A thrown orb that bursts into an effect cloud where it lands (or when its fuse runs out). */
  public static ArenaItemEntry orb(String name, Material material, int amount,
      ArenaPotionItemConfig config, List<String> lore) {
    return new ArenaItemEntry(name, new ItemStack(material), amount, ArenaItemMode.POTION, config,
        false, true, lore);
  }

  /** A consumable that applies its effect to the user on right click. */
  public static ArenaItemEntry spark(String name, Material material, int amount,
      ArenaPotionItemConfig config, List<String> lore) {
    return new ArenaItemEntry(name, new ItemStack(material), amount, ArenaItemMode.SELF_POTION,
        config, false, true, lore);
  }

  private static Enchantment resolveEnchantment(String key) {
    String normalized = key.toLowerCase(Locale.ROOT).replace(' ', '_');
    NamespacedKey namespacedKey = normalized.contains(":") ? NamespacedKey.fromString(normalized)
        : NamespacedKey.minecraft(normalized);
    if (namespacedKey == null) {
      throw new IllegalArgumentException("invalid enchantment key: " + key);
    }
    Registry<Enchantment> enchantmentRegistry =
        RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
    Enchantment enchantment = enchantmentRegistry.get(namespacedKey);
    if (enchantment == null) {
      throw new IllegalArgumentException("unknown enchantment: " + key);
    }
    return enchantment;
  }
}
