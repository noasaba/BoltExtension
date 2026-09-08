package com.noasaba.boltextension.service;

import com.noasaba.boltextension.model.SkipReason;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.popcraft.bolt.BoltPlugin;
import org.popcraft.bolt.access.Access;
import org.popcraft.bolt.event.LockBlockEvent;
import org.popcraft.bolt.util.ProtectableConfig;

import java.util.Optional;
import java.util.UUID;

public final class BoltProtectionEligibilityService {

    private final BoltPlugin bolt;

    public BoltProtectionEligibilityService(BoltPlugin bolt) {
        this.bolt = bolt;
    }

    public SkipReason checkType(Player player, String type) {
        Access access = bolt.getBolt().getAccessRegistry().getProtectionByType(type).orElse(null);
        if (access == null) {
            return SkipReason.PROTECTION_TYPE_UNKNOWN;
        }
        if (access.restricted() && !player.hasPermission("bolt.type.protection." + access.type())) {
            return SkipReason.PROTECTION_TYPE_DENIED;
        }
        return null;
    }

    public SkipReason checkNewProtection(Player player, Block block, String type, boolean fireEvent) {
        if (!bolt.isProtectable(block)) {
            return SkipReason.NOT_PROTECTABLE;
        }

        ProtectableConfig config = bolt.getProtectableConfig(block);
        if (config == null) {
            return SkipReason.NOT_PROTECTABLE;
        }

        SkipReason typeDecision = checkType(player, type);
        if (typeDecision != null) {
            return typeDecision;
        }

        String material = block.getType().name().toLowerCase(java.util.Locale.ROOT);
        if (config.lockPermission() && !player.hasPermission("bolt.protection.lock." + material)) {
            return SkipReason.BLOCK_LOCK_PERMISSION_DENIED;
        }

        if (fireEvent) {
            LockBlockEvent event = new LockBlockEvent(player, block, false);
            bolt.getEventBus().post(event);
            if (event.isCancelled()) {
                return SkipReason.LOCK_EVENT_CANCELLED;
            }
        } else {
            return SkipReason.LOCK_EVENT_NOT_EVALUATED;
        }
        return null;
    }

    public SkipReason checkAccessType(Player player, String type) {
        Access access = bolt.getBolt().getAccessRegistry().getAccessByType(type).orElse(null);
        if (access == null) {
            return SkipReason.ACCESS_TYPE_UNKNOWN;
        }
        if (access.restricted() && !player.hasPermission("bolt.type.access." + access.type())) {
            return SkipReason.ACCESS_TYPE_DENIED;
        }
        return null;
    }

    public String defaultAccessType() {
        return bolt.getDefaultAccessType();
    }

    public Optional<UUID> resolveKnownPlayer(String name) {
        return Optional.ofNullable(bolt.getProfileCache().getProfile(name)).map(profile -> profile.uuid());
    }
}
