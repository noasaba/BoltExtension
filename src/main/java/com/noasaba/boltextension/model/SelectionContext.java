package com.noasaba.boltextension.model;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

public record SelectionContext(
        World world,
        Region region,
        BlockVector3 minimum,
        BlockVector3 maximum,
        long volume
) {

    public boolean contains(Block block) {
        return world.equals(block.getWorld()) && region.contains(BlockVector3.at(
                block.getX(),
                block.getY(),
                block.getZ()
        ));
    }

    public boolean contains(Entity entity) {
        if (!world.equals(entity.getWorld())) {
            return false;
        }
        return region.contains(BlockVector3.at(
                entity.getLocation().getBlockX(),
                entity.getLocation().getBlockY(),
                entity.getLocation().getBlockZ()
        ));
    }

    public String describe() {
        return world.getName() + " " + region.getClass().getSimpleName() + " (" +
                minimum.x() + "," + minimum.y() + "," + minimum.z() + ") -> (" +
                maximum.x() + "," + maximum.y() + "," + maximum.z() + "), volume=" + volume;
    }
}
