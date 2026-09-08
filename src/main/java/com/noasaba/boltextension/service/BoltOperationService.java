package com.noasaba.boltextension.service;

import com.noasaba.boltextension.config.PluginSettings;
import com.noasaba.boltextension.model.AccessDecision;
import com.noasaba.boltextension.model.AdminUnlockPlan;
import com.noasaba.boltextension.model.BoltInspection;
import com.noasaba.boltextension.model.InvalidProtection;
import com.noasaba.boltextension.model.OperationSummary;
import com.noasaba.boltextension.model.SelectionContext;
import com.noasaba.boltextension.model.SkipReason;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.popcraft.bolt.BoltAPI;
import org.popcraft.bolt.protection.BlockProtection;
import org.popcraft.bolt.protection.Protection;
import org.popcraft.bolt.source.Source;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BoltOperationService {

    private final BoltAPI bolt;
    private final PluginSettings settings;
    private final WorldGuardAccessService worldGuardService;
    private final BoltProtectionEligibilityService eligibilityService;
    private final Logger logger;

    public BoltOperationService(
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
        return run("set-" + type, player, selection, execute, (block, summary) -> {
            SkipReason typeDecision = eligibilityService.checkType(player, type);
            if (typeDecision != null) {
                summary.skip(typeDecision);
                return;
            }

            Protection protection = bolt.findProtection(block);
            if (protection != null) {
                if (!summary.markProcessed(protection)) {
                    summary.skip(SkipReason.DUPLICATE_PROTECTION);
                    return;
                }
                if (!isOwner(player, protection)) {
                    summary.skip(SkipReason.OWNER_MISMATCH);
                    return;
                }
                if (type.equals(protection.getType())) {
                    summary.skip(SkipReason.TYPE_ALREADY_SET);
                    return;
                }
                if (!hasWorldGuardAccess(player, block, protection, summary)) {
                    return;
                }
                if (execute) {
                    protection.setType(type);
                    bolt.saveProtection(protection);
                }
                summary.changed();
                return;
            }

            if (!hasWorldGuardAccess(player, block, summary)) {
                return;
            }
            SkipReason eligibility = eligibilityService.checkNewProtection(player, block, type, execute);
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
                BlockProtection created = bolt.createProtection(block, player.getUniqueId(), type);
                bolt.saveProtection(created);
            }
            summary.created();
        });
    }

    public OperationSummary transfer(
            Player player,
            SelectionContext selection,
            UUID targetOwner,
            boolean execute
    ) {
        return run("transfer", player, selection, execute, (block, summary) -> {
            Protection protection = bolt.findProtection(block);
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
            if (!hasWorldGuardAccess(player, block, protection, summary)) {
                return;
            }
            if (execute) {
                protection.setOwner(targetOwner);
                bolt.saveProtection(protection);
            }
            summary.transferred();
        });
    }

    public OperationSummary unlock(
            Player player,
            SelectionContext selection,
            boolean bypassOwner,
            boolean execute
    ) {
        return run("unlock", player, selection, execute, (block, summary) -> {
            Protection protection = bolt.findProtection(block);
            if (protection == null) {
                summary.skip(SkipReason.NO_PROTECTION);
                return;
            }
            if (!summary.markProcessed(protection)) {
                summary.skip(SkipReason.DUPLICATE_PROTECTION);
                return;
            }
            if (!bypassOwner && !isOwner(player, protection)) {
                summary.skip(SkipReason.OWNER_MISMATCH);
                return;
            }
            if (!hasWorldGuardAccess(player, block, protection, summary)) {
                return;
            }
            if (execute) {
                bolt.removeProtection(protection);
            }
            summary.removed();
        });
    }

    public OperationSummary changeAccess(
            Player player,
            SelectionContext selection,
            UUID target,
            String accessType,
            boolean add,
            boolean execute
    ) {
        String source = Source.player(target).toString();
        return run("access-" + (add ? "add" : "remove"), player, selection, execute, (block, summary) -> {
            Protection protection = bolt.findProtection(block);
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
            if (!hasWorldGuardAccess(player, block, protection, summary)) {
                return;
            }
            if (add) {
                SkipReason typeDecision = eligibilityService.checkAccessType(player, accessType);
                if (typeDecision != null) {
                    summary.skip(typeDecision);
                    return;
                }
                if (accessType.equals(protection.getAccess().get(source))) {
                    summary.skip(SkipReason.ACCESS_ALREADY_SET);
                    return;
                }
                if (execute) {
                    protection.getAccess().put(source, accessType);
                    bolt.saveProtection(protection);
                }
                summary.changed();
                return;
            }
            if (!protection.getAccess().containsKey(source)) {
                summary.skip(SkipReason.ACCESS_NOT_PRESENT);
                return;
            }
            if (execute) {
                protection.getAccess().remove(source);
                bolt.saveProtection(protection);
            }
            summary.changed();
        });
    }

    public List<InvalidProtection> findInvalidProtections() {
        List<InvalidProtection> invalid = new ArrayList<>();
        for (Protection protection : bolt.loadProtections()) {
            if (!(protection instanceof BlockProtection blockProtection)) {
                continue;
            }
            Block block = resolveProtectionBlock(blockProtection);
            if (block != null && !bolt.isProtectable(block)) {
                invalid.add(new InvalidProtection(
                        block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                        protection.getOwner(), protection.getType(), block.getType().name()
                ));
            }
        }
        return List.copyOf(invalid);
    }

    public Optional<UUID> resolveKnownPlayer(String name) {
        return eligibilityService.resolveKnownPlayer(name);
    }

    public String defaultAccessType() {
        return eligibilityService.defaultAccessType();
    }

    public AdminUnlockPlan prepareAdminUnlock(Player player, SelectionContext selection) {
        OperationSummary summary = new OperationSummary();
        List<Protection> protections = new ArrayList<>();
        debug("prepare-admin-unlock", player, selection, summary, false, "start");
        scan(selection, summary, (block, currentSummary) -> {
            Protection protection = bolt.findProtection(block);
            if (protection == null) {
                currentSummary.skip(SkipReason.NO_PROTECTION);
                return;
            }
            if (!currentSummary.markProcessed(protection)) {
                currentSummary.skip(SkipReason.DUPLICATE_PROTECTION);
                return;
            }
            if (!hasWorldGuardAccess(player, block, protection, currentSummary)) {
                return;
            }
            protections.add(protection);
            currentSummary.removed();
        });
        debug("prepare-admin-unlock", player, selection, summary, false, "finish");
        return new AdminUnlockPlan(selection, protections, summary);
    }

    public OperationSummary executeAdminUnlock(Player player, AdminUnlockPlan plan) {
        OperationSummary summary = new OperationSummary();
        for (Protection planned : plan.protections()) {
            summary.scanned();
            try {
                Protection current = resolveCurrentProtection(planned);
                if (current == null || !current.getId().equals(planned.getId())) {
                    summary.skip(SkipReason.NO_PROTECTION);
                    continue;
                }
                if (!hasWorldGuardAccess(player, current, summary)) {
                    continue;
                }
                bolt.removeProtection(current);
                summary.removed();
            } catch (Exception exception) {
                recordFailure(summary, describeProtection(planned), exception);
            }
        }
        return summary;
    }

    public BoltInspection inspect(Player player, Block block) {
        Protection exact = bolt.loadProtection(block);
        Protection matched = bolt.findProtection(block);
        boolean editAccess = false;
        if (matched != null) {
            try {
                editAccess = bolt.canAccess(matched, player, "edit");
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Bolt edit access判定に失敗しました", exception);
            }
        }
        return new BoltInspection(
                block,
                bolt.isProtectable(block),
                bolt.isProtectedExact(block),
                exact,
                matched,
                matched != null && isOwner(player, matched),
                editAccess
        );
    }

    public String describeProtection(Protection protection) {
        if (protection == null) {
            return "none";
        }
        String description = "id=" + protection.getId() +
                ",owner=" + protection.getOwner() +
                ",type=" + protection.getType();
        if (protection instanceof BlockProtection blockProtection) {
            description += ",block=" + blockProtection.getWorld() + ":" +
                    blockProtection.getX() + "," + blockProtection.getY() + "," + blockProtection.getZ();
        }
        return description;
    }

    private OperationSummary run(
            String operation,
            Player player,
            SelectionContext selection,
            boolean execute,
            BlockOperation operationAction
    ) {
        OperationSummary summary = new OperationSummary();
        debug(operation, player, selection, summary, execute, "start");
        scan(selection, summary, operationAction);
        debug(operation, player, selection, summary, execute, "finish");
        return summary;
    }

    private void scan(SelectionContext selection, OperationSummary summary, BlockOperation action) {
        for (BlockVector3 position : selection.region()) {
            summary.scanned();
            Block block = selection.world().getBlockAt(position.x(), position.y(), position.z());
            try {
                action.apply(block, summary);
            } catch (Exception exception) {
                recordFailure(summary, describeBlock(block), exception);
            }
        }
    }

    private Protection resolveCurrentProtection(Protection planned) {
        if (!(planned instanceof BlockProtection blockProtection)) {
            return planned;
        }
        Block block = resolveProtectionBlock(blockProtection);
        if (block == null) {
            return null;
        }
        return bolt.findProtection(block);
    }

    private boolean hasWorldGuardAccess(Player player, Protection protection, OperationSummary summary) {
        if (!(protection instanceof BlockProtection blockProtection)) {
            return true;
        }

        Block protectionBlock = resolveProtectionBlock(blockProtection);
        if (protectionBlock == null) {
            summary.skip(SkipReason.PROTECTION_BLOCK_UNAVAILABLE);
            return false;
        }
        AccessDecision access = worldGuardService.checkBlock(player, protectionBlock);
        if (access.allowed()) {
            return true;
        }

        summary.skip(SkipReason.WORLDGUARD_DENIED);
        if (settings.debugLogging()) {
            logger.info("[debug][" + summary.operationId() + "] matched protection denied " +
                    describeProtection(protection) + " worldGuard={" + access.describe() + "}");
        }
        return false;
    }

    private boolean hasWorldGuardAccess(
            Player player,
            Block selectedBlock,
            Protection protection,
            OperationSummary summary
    ) {
        return hasWorldGuardAccess(player, selectedBlock, summary)
                && hasWorldGuardAccess(player, protection, summary);
    }

    private boolean hasWorldGuardAccess(Player player, Block block, OperationSummary summary) {
        AccessDecision access = worldGuardService.checkBlock(player, block);
        if (access.allowed()) {
            return true;
        }
        summary.skip(SkipReason.WORLDGUARD_DENIED);
        return false;
    }

    private Block resolveProtectionBlock(BlockProtection protection) {
        World world = Bukkit.getWorld(protection.getWorld());
        if (world == null) {
            return null;
        }
        return world.getBlockAt(protection.getX(), protection.getY(), protection.getZ());
    }

    private String describeBlock(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private void recordFailure(OperationSummary summary, String context, Exception exception) {
        String sample = context + " " + exception.getClass().getSimpleName() + ": " + exception.getMessage();
        int before = summary.errorSamples().size();
        summary.failed(sample, settings.maxErrorSamples());
        if (before < settings.maxErrorSamples()) {
            logger.log(Level.WARNING, "Bolt操作失敗 [" + summary.operationId() + "] " + context, exception);
        }
    }

    private boolean isOwner(Player player, Protection protection) {
        return player.getUniqueId().equals(protection.getOwner());
    }

    private void debug(
            String operation,
            Player player,
            SelectionContext selection,
            OperationSummary summary,
            boolean execute,
            String phase
    ) {
        if (!settings.debugLogging()) {
            return;
        }
        logger.info("[debug][" + summary.operationId() + "] " + phase +
                " operation=" + operation +
                " execute=" + execute +
                " player=" + player.getName() +
                " selection=" + selection.describe() +
                " result={" + summary.describeMain() + "}");
    }

    @FunctionalInterface
    private interface BlockOperation {
        void apply(Block block, OperationSummary summary) throws Exception;
    }
}
