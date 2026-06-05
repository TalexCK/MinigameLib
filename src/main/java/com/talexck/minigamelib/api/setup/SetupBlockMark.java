package com.talexck.minigamelib.api.setup;

import com.talexck.minigamelib.api.arena.ArenaPoint;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public record SetupBlockMark(
    Player player,
    Block block,
    Location location,
    ArenaPoint point) {
}
