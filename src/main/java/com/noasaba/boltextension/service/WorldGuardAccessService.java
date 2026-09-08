package com.noasaba.boltextension.service;

import com.noasaba.boltextension.config.PluginSettings;
import com.noasaba.boltextension.model.AccessDecision;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WorldGuardAccessService {

    private final PluginSettings settings;
    private final StateFlag extensionFlag;
    private final Logger logger;

    public WorldGuardAccessService(PluginSettings settings, StateFlag extensionFlag, Logger logger) {
        this.settings = settings;
        this.extensionFlag = extensionFlag;
        this.logger = logger;
    }

    public AccessDecision checkBlock(Player player, Block block) {
        if (!settings.worldGuardEnabled()) {
            return AccessDecision.allow("integration-disabled", List.of());
        }

        try {
            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            if (hasBypass(localPlayer)) {
                return AccessDecision.allow("worldguard-bypass", List.of());
            }

            Location location = block.getLocation();
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));
            return evaluate(localPlayer, regions);
        } catch (Exception exception) {
            logger.log(Level.WARNING, "WorldGuardのブロック判定に失敗しました", exception);
            return AccessDecision.deny("worldguard-error", List.of());
        }
    }

    private AccessDecision evaluate(LocalPlayer player, ApplicableRegionSet regions) {
        List<String> regionIds = regions.getRegions().stream()
                .map(ProtectedRegion::getId)
                .sorted()
                .toList();

        if (regions.isVirtual()) {
            return AccessDecision.deny("region-data-unavailable", regionIds);
        }
        if (extensionFlag == null) {
            return AccessDecision.deny("extension-flag-unavailable", regionIds);
        }

        StateFlag.State extensionState = regions.queryState(player, extensionFlag);
        if (extensionState == null) {
            extensionState = extensionFlag.getDefault();
        }
        if (extensionState != StateFlag.State.ALLOW) {
            return AccessDecision.deny("extension-flag-denied", regionIds);
        }

        StateFlag.State buildState = regions.queryState(player, Flags.BUILD);
        if (settings.worldGuardRequireBuildAccess() && buildState == StateFlag.State.DENY) {
            return AccessDecision.deny("build-denied", regionIds);
        }

        if (regions.size() == 0) {
            return noRegionDecision();
        }

        if (settings.worldGuardRequireBuildAccess() && buildState != StateFlag.State.ALLOW) {
            return AccessDecision.deny("build-not-allowed", regionIds);
        }

        if (settings.worldGuardRequireMembership() && !regions.isMemberOfAll(player)) {
            return AccessDecision.deny("not-member-of-all-regions", regionIds);
        }

        return AccessDecision.allow("worldguard-policy-allowed", regionIds);
    }

    private AccessDecision noRegionDecision() {
        return settings.worldGuardAllowNoRegion()
                ? AccessDecision.allow("no-region-allowed", List.of())
                : AccessDecision.deny("no-region-denied", List.of());
    }

    private boolean hasBypass(LocalPlayer player) {
        if (!settings.worldGuardHonorBypassPermission()) {
            return false;
        }
        return WorldGuard.getInstance()
                .getPlatform()
                .getSessionManager()
                .hasBypass(player, player.getWorld());
    }
}
