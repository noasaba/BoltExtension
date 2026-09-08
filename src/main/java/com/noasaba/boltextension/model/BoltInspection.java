package com.noasaba.boltextension.model;

import org.bukkit.block.Block;
import org.popcraft.bolt.protection.Protection;

public record BoltInspection(
        Block block,
        boolean protectable,
        boolean protectedExact,
        Protection exactProtection,
        Protection matchedProtection,
        boolean ownerMatch,
        boolean editAccess
) {
}
