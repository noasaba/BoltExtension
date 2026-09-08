package com.noasaba.boltextension.service;

import com.noasaba.boltextension.model.SelectionContext;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.entity.Player;

public final class WorldEditSelectionService {

    public SelectionContext selection(Player player) throws IncompleteRegionException {
        Region region = WorldEdit.getInstance()
                .getSessionManager()
                .get(BukkitAdapter.adapt(player))
                .getSelection(BukkitAdapter.adapt(player.getWorld()))
                .clone();

        BlockVector3 minimum = region.getMinimumPoint();
        BlockVector3 maximum = region.getMaximumPoint();
        return new SelectionContext(player.getWorld(), region, minimum, maximum, region.getVolume());
    }
}
