package com.noasaba.boltextension.service;

import com.noasaba.boltextension.model.SkipReason;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.popcraft.bolt.BoltPlugin;
import org.popcraft.bolt.access.Access;
import org.popcraft.bolt.event.LockBlockEvent;
import org.popcraft.bolt.event.LockEntityEvent;
import org.popcraft.bolt.source.SourceTypes;
import org.popcraft.bolt.util.ProtectableConfig;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
            return postLockBlockEvent(player, block);
        }
        return SkipReason.LOCK_EVENT_NOT_EVALUATED;
    }

    public SkipReason checkNewProtection(Player player, Entity entity, String type, boolean fireEvent) {
        if (!bolt.isProtectable(entity)) {
            return SkipReason.NOT_PROTECTABLE;
        }

        ProtectableConfig config = bolt.getProtectableConfig(entity);
        if (config == null) {
            return SkipReason.NOT_PROTECTABLE;
        }

        SkipReason typeDecision = checkType(player, type);
        if (typeDecision != null) {
            return typeDecision;
        }

        String entityType = entity.getType().name().toLowerCase(java.util.Locale.ROOT);
        if (config.lockPermission() && !player.hasPermission("bolt.protection.lock." + entityType)) {
            return SkipReason.ENTITY_LOCK_PERMISSION_DENIED;
        }

        if (fireEvent) {
            return postLockEntityEvent(player, entity);
        }
        return SkipReason.LOCK_EVENT_NOT_EVALUATED;
    }

    private SkipReason postLockBlockEvent(Player player, Block block) {
        try {
            LockBlockEvent event = new LockBlockEvent(player, block, false);
            postBoltEvent(event);
            return event.isCancelled() ? SkipReason.LOCK_EVENT_CANCELLED : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return SkipReason.LOCK_EVENT_UNAVAILABLE;
        }
    }

    private SkipReason postLockEntityEvent(Player player, Entity entity) {
        try {
            LockEntityEvent event = new LockEntityEvent(player, entity, false);
            postBoltEvent(event);
            return event.isCancelled() ? SkipReason.LOCK_EVENT_CANCELLED : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return SkipReason.LOCK_EVENT_UNAVAILABLE;
        }
    }

    private void postBoltEvent(Object event) throws ReflectiveOperationException {
        Object eventBus = bolt.getClass().getMethod("getEventBus").invoke(bolt);
        for (var method : eventBus.getClass().getMethods()) {
            if (method.getName().equals("post") && method.getParameterCount() == 1) {
                method.invoke(eventBus, event);
                return;
            }
        }
        throw new NoSuchMethodException("Bolt EventBus.post(Event)");
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

    public java.util.List<String> protectionTypes() {
        return bolt.getBolt().getAccessRegistry().protectionTypes().stream().sorted().toList();
    }

    public CompletableFuture<Optional<String>> resolveKnownGroupAsync(String name) {
        return bolt.getBolt().getStore().loadGroup(name)
                .thenApply(group -> Optional.ofNullable(group).map(value -> value.getName()))
                .exceptionally(exception -> Optional.empty());
    }

    public String groupSource(String name) {
        return org.popcraft.bolt.source.Source.of(SourceTypes.GROUP, name).toString();
    }

    public Optional<UUID> resolveKnownPlayer(String name) {
        return Optional.ofNullable(bolt.getProfileCache().getProfile(name)).map(profile -> profile.uuid());
    }

    public java.util.List<String> accessTypes() {
        return bolt.getBolt().getAccessRegistry().accessTypes().stream().sorted().toList();
    }

    public java.util.List<String> ownedGroups(Player player) {
        return bolt.getPlayersOwnedGroups(player).stream().sorted().toList();
    }
}
