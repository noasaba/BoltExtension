package com.noasaba.boltextension.service;

import com.noasaba.boltextension.model.AccessDecision;
import com.noasaba.boltextension.model.OperationSummary;
import com.noasaba.boltextension.model.SelectionContext;
import com.noasaba.boltextension.model.SkipReason;
import com.noasaba.boltextension.config.PluginSettings;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.popcraft.bolt.BoltAPI;
import org.popcraft.bolt.protection.EntityProtection;
import org.popcraft.bolt.protection.Protection;
import org.popcraft.bolt.util.Permission;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EntityOperationService {

    private final BoltAPI bolt;
    private final WorldGuardAccessService worldGuardService;
    private final BoltProtectionEligibilityService eligibilityService;
    private final PluginSettings settings;
    private final Logger logger;

    public EntityOperationService(
            BoltAPI bolt,
            PluginSettings settings,
            WorldGuardAccessService worldGuardService,
            BoltProtectionEligibilityService eligibilityService,
            Logger logger
    ) {
        this.bolt = bolt;
        this.settings = settings;
        this.worldGuardService = worldGuardService;
        this.eligibilityService = eligibilityService;
        this.logger = logger;
    }

    public OperationSummary setProtection(Player player, SelectionContext selection, String type, boolean execute) {
        return run(player, selection, (entity, summary) -> {
            SkipReason typeDecision = eligibilityService.checkType(player, type);
            if (typeDecision != null) {
                summary.skip(typeDecision);
                return;
            }

            Protection protection = bolt.findProtection(entity);
            if (protection != null) {
                if (!summary.markProcessed(protection)) {
                    summary.skip(SkipReason.DUPLICATE_PROTECTION);
                    return;
                }
                if (!canEdit(player, protection)) {
                    summary.skip(SkipReason.EDIT_ACCESS_DENIED);
                    return;
                }
                if (type.equals(protection.getType())) {
                    summary.skip(SkipReason.TYPE_ALREADY_SET);
                    return;
                }
                if (!hasWorldGuardAccess(player, entity, summary)) {
                    return;
                }
                if (execute) {
                    protection.setType(type);
                    bolt.saveProtection(protection);
                }
                summary.changed();
                return;
            }

            if (!hasWorldGuardAccess(player, entity, summary)) {
                return;
            }
            SkipReason eligibility = eligibilityService.checkNewProtection(player, entity, type, execute);
            if (eligibility == SkipReason.LOCK_EVENT_NOT_EVALUATED) {
                summary.skip(eligibility);
                summary.created();
                return;
            }
            if (eligibility != null) {
                summary.skip(eligibility);
                return;
            }
            if (execute) {
                EntityProtection created = bolt.createProtection(entity, player.getUniqueId(), type);
                bolt.saveProtection(created);
            }
            summary.created();
        });
    }

    public OperationSummary transfer(Player player, SelectionContext selection, UUID targetOwner, boolean execute) {
        return run(player, selection, (entity, summary) -> {
            Protection protection = bolt.findProtection(entity);
            if (protection == null) {
                summary.skip(SkipReason.NO_PROTECTION);
                return;
            }
            if (!summary.markProcessed(protection)) {
                summary.skip(SkipReason.DUPLICATE_PROTECTION);
                return;
            }
            if (!isOwner(player, protection)) {
                summary.skip(SkipReason.OWNER_MISMATCH);
                return;
            }
            if (targetOwner.equals(protection.getOwner())) {
                summary.skip(SkipReason.TRANSFER_TARGET_SAME_AS_OWNER);
                return;
            }
            if (!hasWorldGuardAccess(player, entity, summary)) {
                return;
            }
            if (execute) {
                protection.setOwner(targetOwner);
                bolt.saveProtection(protection);
            }
            summary.transferred();
        });
    }

    public OperationSummary unlock(Player player, SelectionContext selection, boolean execute) {
        return run(player, selection, (entity, summary) -> {
            Protection protection = bolt.findProtection(entity);
            if (protection == null) {
                summary.skip(SkipReason.NO_PROTECTION);
                return;
            }
            if (!summary.markProcessed(protection)) {
                summary.skip(SkipReason.DUPLICATE_PROTECTION);
                return;
            }
            if (!isOwner(player, protection)) {
                summary.skip(SkipReason.OWNER_MISMATCH);
                return;
            }
            if (!hasWorldGuardAccess(player, entity, summary)) {
                return;
            }
            if (execute) {
                bolt.removeProtection(protection);
            }
            summary.removed();
        });
    }

    private OperationSummary run(Player player, SelectionContext selection, EntityAction action) {
        OperationSummary summary = new OperationSummary();
        for (Entity entity : selection.world().getEntities()) {
            if (!selection.contains(entity)) {
                continue;
            }
            summary.scanned();
            try {
                action.apply(entity, summary);
            } catch (RuntimeException exception) {
                summary.failed(entity.getUniqueId() + " " + exception.getClass().getSimpleName(), settings.maxErrorSamples());
                logger.log(Level.WARNING, "Entity保護操作に失敗しました: " + entity.getUniqueId(), exception);
            }
        }
        return summary;
    }

    private boolean hasWorldGuardAccess(Player player, Entity entity, OperationSummary summary) {
        Block block = entity.getLocation().getBlock();
        AccessDecision decision = worldGuardService.checkBlock(player, block);
        if (decision.allowed()) {
            return true;
        }
        summary.skip(SkipReason.WORLDGUARD_DENIED);
        return false;
    }

    private boolean canEdit(Player player, Protection protection) {
        if (isOwner(player, protection)) {
            return true;
        }
        try {
            return bolt.canAccess(protection, player, Permission.EDIT);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Bolt entity editアクセス判定に失敗しました", exception);
            return false;
        }
    }

    private boolean isOwner(Player player, Protection protection) {
        return player.getUniqueId().equals(protection.getOwner());
    }

    @FunctionalInterface
    private interface EntityAction {
        void apply(Entity entity, OperationSummary summary);
    }
}
