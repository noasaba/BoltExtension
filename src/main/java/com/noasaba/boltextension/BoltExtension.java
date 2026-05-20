package com.noasaba.boltextension;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.popcraft.bolt.BoltAPI;
import org.popcraft.bolt.protection.BlockProtection;
import org.popcraft.bolt.protection.Protection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class BoltExtension extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final long CONFIRM_TIMEOUT_MILLIS = 60_000L;
    private static StateFlag BOLT_EXTENSION_FLAG;

    private BoltAPI bolt;
    private long maxVolumeThreshold;
    private final Map<UUID, AdminPlan> adminConfirmMap = new HashMap<>();
    private boolean wgEnabled = false;

    @Override
    public void onLoad() {
        if (getConfig().getBoolean("worldguard.enabled", false)) {
            try {
                BOLT_EXTENSION_FLAG = new StateFlag(
                        "bolt-extension-allow",
                        getConfig().getBoolean("worldguard.flag-default", true)
                );
                WorldGuard.getInstance().getFlagRegistry().register(BOLT_EXTENSION_FLAG);
                wgEnabled = true;
            } catch (FlagConflictException e) {
                getLogger().warning("フラグが既に存在します: " + e.getMessage());
                wgEnabled = false;
            }
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        maxVolumeThreshold = getConfig().getLong("max-volume", 1_000_000L);

        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
            getLogger().severe("WorldEditが見つかりません");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.bolt = Bukkit.getServicesManager().load(BoltAPI.class);
        if (this.bolt == null) {
            getLogger().severe("BoltAPIが見つかりません");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (wgEnabled && WorldGuardPlugin.inst() == null) {
            getLogger().warning("WorldGuardが無効です");
            wgEnabled = false;
        }

        if (getCommand("boltext") == null) {
            getLogger().severe("plugin.yml に boltext コマンドが定義されていません");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        getCommand("boltext").setExecutor(this);
        getCommand("boltext").setTabCompleter(this);
        getLogger().info("== === ==");
        getLogger().info(getDescription().getName() + " v" + getDescription().getVersion());
        getLogger().info(" Developed by NOASABA (by nanosize)");
        getLogger().info("== === ==");
    }

    private boolean checkWorldGuardAccess(Player player, Region selection) {
        if (!wgEnabled) return true;

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager manager = container.get(BukkitAdapter.adapt(player.getWorld()));
            if (manager == null) {
                return getConfig().getBoolean("worldguard.allow-no-region", false);
            }

            ProtectedCuboidRegion selectedRegion = new ProtectedCuboidRegion(
                    "__boltext_selection",
                    selection.getMinimumPoint(),
                    selection.getMaximumPoint()
            );
            ApplicableRegionSet regions = manager.getApplicableRegions(selectedRegion);
            if (regions.size() == 0) {
                return getConfig().getBoolean("worldguard.allow-no-region", false);
            }
            for (ProtectedRegion region : regions) {
                if (!hasRegionAccess(player, region)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "WorldGuard権限チェックエラー", e);
            return false;
        }
    }

    private boolean checkPoint(Player player, int x, int y, int z) {
        if (!wgEnabled) return true;

        Location loc = new Location(player.getWorld(), x, y, z);
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(loc));
        if (regions.size() == 0) {
            return getConfig().getBoolean("worldguard.allow-no-region", false);
        }
        for (ProtectedRegion region : regions) {
            if (!hasRegionAccess(player, region)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasRegionAccess(Player player, ProtectedRegion region) {
        StateFlag.State flagState = region.getFlag(BOLT_EXTENSION_FLAG);
        boolean flagAllowed = (flagState == null) ?
                getConfig().getBoolean("worldguard.flag-default", true) :
                (flagState == StateFlag.State.ALLOW);
        if (!flagAllowed) {
            return false;
        }

        boolean isOwner = region.getOwners().contains(player.getUniqueId()) ||
                region.getOwners().contains(player.getName());
        boolean isMember = region.getMembers().contains(player.getUniqueId()) ||
                region.getMembers().contains(player.getName());
        return isOwner || isMember;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "ゲーム内プレイヤーのみ使用可能");
            return true;
        }

        if (args.length < 1) {
            showUsage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        try {
            if ("inspect".equals(subCommand)) {
                handleInspect(player);
                return true;
            }
            if ("confirm".equals(subCommand)) {
                handleConfirm(player);
                return true;
            }

            Region selection = getSelection(player);
            if (!isVolumeAllowed(player, selection)) {
                return true;
            }

            boolean bypassWorldGuard = isAdminUnlock(args);
            if (!bypassWorldGuard && !checkWorldGuardAccess(player, selection)) {
                player.sendMessage(ChatColor.RED + "この領域での操作権限がありません");
                return true;
            }

            return processCommand(player, selection, args);
        } catch (IncompleteRegionException e) {
            player.sendMessage(ChatColor.RED + "範囲選択が不完全です");
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "エラーが発生しました");
            getLogger().log(Level.SEVERE, "コマンド処理中にエラーが発生しました", e);
        }
        return true;
    }

    private Region getSelection(Player player) throws IncompleteRegionException {
        SessionManager sessionManager = WorldEdit.getInstance().getSessionManager();
        return sessionManager.get(BukkitAdapter.adapt(player))
                .getSelection(BukkitAdapter.adapt(player.getWorld()));
    }

    private boolean isVolumeAllowed(Player player, Region selection) {
        BlockVector3 min = selection.getMinimumPoint();
        BlockVector3 max = selection.getMaximumPoint();
        long volume = ((long) max.x() - min.x() + 1L) *
                ((long) max.y() - min.y() + 1L) *
                ((long) max.z() - min.z() + 1L);
        if (maxVolumeThreshold > 0 && volume > maxVolumeThreshold) {
            player.sendMessage(ChatColor.RED + String.format(
                    "選択範囲が大きすぎます（最大許容: %,d ブロック）", maxVolumeThreshold));
            return false;
        }
        return true;
    }

    private boolean processCommand(Player player, Region region, String[] args) {
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "public":
            case "private":
                sendSummary(player, handleProtection(player, region, subCommand, true), "更新");
                break;
            case "transfer":
                handleTransferCommand(player, region, args, true);
                break;
            case "unlock":
                sendSummary(player, handleUnlock(player, region, false, true), "削除");
                break;
            case "admin":
                handleAdmin(player, region, args);
                break;
            case "scan":
                handleScan(player, region, args);
                break;
            default:
                player.sendMessage(ChatColor.RED + "不明なサブコマンド: " + subCommand);
                showUsage(player);
        }
        return true;
    }

    private void showUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "使い方: /boltext <public|private|transfer|unlock|admin|confirm|inspect|scan> [args...]");
    }

    private OperationSummary handleProtection(Player player, Region region, String type, boolean execute) {
        OperationSummary summary = new OperationSummary();
        scanBlocks(player, region, summary, block -> {
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
                if (execute) {
                    protection.setType(type);
                    bolt.saveProtection(protection);
                }
                summary.changed++;
                return;
            }

            if (!bolt.isProtectable(block)) {
                summary.skip(SkipReason.NOT_PROTECTABLE);
                return;
            }
            if (execute) {
                BlockProtection newProtection = bolt.createProtection(block, player.getUniqueId(), type);
                bolt.saveProtection(newProtection);
            }
            summary.created++;
        });
        return summary;
    }

    private void handleTransferCommand(Player player, Region region, String[] args, boolean execute) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "使い方: /boltext transfer <targetPlayer>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "指定されたプレイヤーはオンラインではありません");
            return;
        }
        sendSummary(player, handleTransfer(player, region, target.getUniqueId(), execute), "移譲");
    }

    private OperationSummary handleTransfer(Player player, Region region, UUID targetUUID, boolean execute) {
        OperationSummary summary = new OperationSummary();
        scanBlocks(player, region, summary, block -> {
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
            if (protection.getOwner().equals(targetUUID)) {
                summary.skip(SkipReason.TRANSFER_TARGET_SAME_AS_OWNER);
                return;
            }
            if (execute) {
                protection.setOwner(targetUUID);
                bolt.saveProtection(protection);
            }
            summary.transferred++;
        });
        return summary;
    }

    private OperationSummary handleUnlock(Player player, Region region, boolean admin, boolean execute) {
        OperationSummary summary = new OperationSummary();
        scanBlocks(player, region, summary, block -> {
            Protection protection = bolt.findProtection(block);
            if (protection == null) {
                summary.skip(SkipReason.NO_PROTECTION);
                return;
            }
            if (!summary.markProcessed(protection)) {
                summary.skip(SkipReason.DUPLICATE_PROTECTION);
                return;
            }
            if (!admin && !isOwner(player, protection)) {
                summary.skip(SkipReason.OWNER_MISMATCH);
                return;
            }
            if (execute) {
                bolt.removeProtection(protection);
            }
            summary.removed++;
        });
        return summary;
    }

    private void handleAdmin(Player player, Region region, String[] args) {
        if (args.length < 2 || !"unlock".equalsIgnoreCase(args[1])) {
            player.sendMessage(ChatColor.RED + "使い方: /boltext admin unlock");
            return;
        }
        if (!player.hasPermission("bolt.extension.admin")) {
            player.sendMessage(ChatColor.RED + "あなたは管理者権限を持っていません");
            return;
        }

        AdminPlan plan = buildAdminUnlockPlan(player, region);
        adminConfirmMap.put(player.getUniqueId(), plan);
        OperationSummary summary = plan.summary;
        player.sendMessage(ChatColor.YELLOW + "警告: 他人の保護も削除されます。60秒以内に /boltext confirm と入力してください");
        sendSummary(player, summary, "削除予定");
    }

    private AdminPlan buildAdminUnlockPlan(Player player, Region region) {
        OperationSummary summary = new OperationSummary();
        List<Protection> protections = new ArrayList<>();
        scanBlocks(player, region, summary, block -> {
            Protection protection = bolt.findProtection(block);
            if (protection == null) {
                summary.skip(SkipReason.NO_PROTECTION);
                return;
            }
            if (!summary.markProcessed(protection)) {
                summary.skip(SkipReason.DUPLICATE_PROTECTION);
                return;
            }
            protections.add(protection);
            summary.removed++;
        });
        return new AdminPlan(player.getWorld().getName(), region.getMinimumPoint(), region.getMaximumPoint(), protections, summary);
    }

    private void handleConfirm(Player player) {
        AdminPlan plan = adminConfirmMap.remove(player.getUniqueId());
        if (plan == null) {
            player.sendMessage(ChatColor.RED + "確認状態ではありません");
            return;
        }
        if (!player.hasPermission("bolt.extension.admin")) {
            player.sendMessage(ChatColor.RED + "あなたは管理者権限を持っていません");
            return;
        }
        if (System.currentTimeMillis() - plan.createdAt > CONFIRM_TIMEOUT_MILLIS) {
            player.sendMessage(ChatColor.RED + "確認の有効期限が切れました。もう一度 /boltext admin unlock を実行してください");
            return;
        }

        OperationSummary executed = new OperationSummary();
        for (Protection protection : plan.protections) {
            try {
                bolt.removeProtection(protection);
                executed.removed++;
            } catch (Exception e) {
                executed.failed++;
                getLogger().log(Level.WARNING, "admin unlock の削除に失敗しました: " + protection, e);
            }
        }
        player.sendMessage(ChatColor.GRAY + "対象: " + plan.worldName + " " + formatBounds(plan.min, plan.max));
        sendSummary(player, executed, "削除");
    }

    private void handleScan(Player player, Region region, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "使い方: /boltext scan <public|private|transfer|unlock|admin unlock> [args...]");
            return;
        }
        String target = args[1].toLowerCase();
        switch (target) {
            case "public":
            case "private":
                sendSummary(player, handleProtection(player, region, target, false), "更新予定");
                break;
            case "transfer":
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "使い方: /boltext scan transfer <targetPlayer>");
                    return;
                }
                Player transferTarget = Bukkit.getPlayerExact(args[2]);
                if (transferTarget == null) {
                    player.sendMessage(ChatColor.RED + "指定されたプレイヤーはオンラインではありません");
                    return;
                }
                sendSummary(player, handleTransfer(player, region, transferTarget.getUniqueId(), false), "移譲予定");
                break;
            case "unlock":
                sendSummary(player, handleUnlock(player, region, false, false), "削除予定");
                break;
            case "admin":
                if (args.length >= 3 && "unlock".equalsIgnoreCase(args[2])) {
                    if (!player.hasPermission("bolt.extension.admin")) {
                        player.sendMessage(ChatColor.RED + "あなたは管理者権限を持っていません");
                        return;
                    }
                    sendSummary(player, handleUnlock(player, region, true, false), "削除予定");
                    return;
                }
                player.sendMessage(ChatColor.RED + "使い方: /boltext scan admin unlock");
                break;
            default:
                player.sendMessage(ChatColor.RED + "不明な scan 対象: " + target);
        }
    }

    private void handleInspect(Player player) {
        Block block = player.getTargetBlockExact(100);
        if (block == null) {
            player.sendMessage(ChatColor.RED + "見ているブロックが見つかりません");
            return;
        }

        Protection exact = bolt.loadProtection(block);
        Protection matched = bolt.findProtection(block);
        boolean protectable = bolt.isProtectable(block);
        boolean exactProtected = bolt.isProtectedExact(block);
        boolean ownerMatch = matched != null && isOwner(player, matched);
        boolean editAccess = false;
        if (matched != null) {
            try {
                editAccess = bolt.canAccess(matched, player, "edit");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Bolt access 判定に失敗しました", e);
            }
        }
        boolean wgPointAllowed = checkPoint(player, block.getX(), block.getY(), block.getZ());

        player.sendMessage(ChatColor.YELLOW + "BoltExtension inspect");
        player.sendMessage(ChatColor.GRAY + "block: " + block.getWorld().getName() + " " +
                block.getX() + "," + block.getY() + "," + block.getZ() + " " + block.getType());
        player.sendMessage(ChatColor.GRAY + "isProtectable: " + protectable);
        player.sendMessage(ChatColor.GRAY + "isProtectedExact: " + exactProtected);
        player.sendMessage(ChatColor.GRAY + "loadProtection: " + describeProtection(exact));
        player.sendMessage(ChatColor.GRAY + "findProtection: " + describeProtection(matched));
        player.sendMessage(ChatColor.GRAY + "ownerMatch: " + ownerMatch + ", boltEditAccess: " + editAccess);
        player.sendMessage(ChatColor.GRAY + "worldGuardAllowedHere: " + wgPointAllowed);
    }

    private void scanBlocks(Player player, Region region, OperationSummary summary, BlockAction action) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    summary.scanned++;
                    try {
                        action.accept(new Location(player.getWorld(), x, y, z).getBlock());
                    } catch (Exception e) {
                        summary.failed++;
                        getLogger().log(Level.WARNING, "ブロック処理に失敗しました: " +
                                player.getWorld().getName() + " " + x + "," + y + "," + z, e);
                    }
                }
            }
        }
    }

    private boolean isOwner(Player player, Protection protection) {
        return protection.getOwner().equals(player.getUniqueId());
    }

    private boolean isAdminUnlock(String[] args) {
        return (args.length >= 2 && "admin".equalsIgnoreCase(args[0]) && "unlock".equalsIgnoreCase(args[1])) ||
                (args.length >= 3 && "scan".equalsIgnoreCase(args[0]) &&
                        "admin".equalsIgnoreCase(args[1]) && "unlock".equalsIgnoreCase(args[2]));
    }

    private void sendSummary(Player player, OperationSummary summary, String actionLabel) {
        player.sendMessage(ChatColor.GREEN + actionLabel + "結果: " + summary.describeMain());
        String skips = summary.describeSkips();
        if (!skips.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "スキップ: " + skips);
        }
        if (summary.failed > 0) {
            player.sendMessage(ChatColor.RED + "失敗: " + summary.failed + " 件。詳細はサーバーログを確認してください");
        }
    }

    private String describeProtection(Protection protection) {
        if (protection == null) {
            return "なし";
        }
        return "id=" + protection.getId() + ", owner=" + protection.getOwner() + ", type=" + protection.getType();
    }

    private String formatBounds(BlockVector3 min, BlockVector3 max) {
        return "(" + min.x() + "," + min.y() + "," + min.z() + ") -> (" +
                max.x() + "," + max.y() + "," + max.z() + ")";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("public", "private", "transfer", "unlock", "admin", "confirm", "inspect", "scan");
            for (String sub : subCommands) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "transfer":
                    addOnlinePlayerCompletions(completions, args[1]);
                    break;
                case "admin":
                    if ("unlock".startsWith(args[1].toLowerCase())) {
                        completions.add("unlock");
                    }
                    break;
                case "scan":
                    for (String sub : Arrays.asList("public", "private", "transfer", "unlock", "admin")) {
                        if (sub.startsWith(args[1].toLowerCase())) {
                            completions.add(sub);
                        }
                    }
                    break;
            }
        } else if (args.length == 3) {
            if ("scan".equalsIgnoreCase(args[0]) && "transfer".equalsIgnoreCase(args[1])) {
                addOnlinePlayerCompletions(completions, args[2]);
            } else if ("scan".equalsIgnoreCase(args[0]) && "admin".equalsIgnoreCase(args[1]) &&
                    "unlock".startsWith(args[2].toLowerCase())) {
                completions.add("unlock");
            }
        }
        return completions;
    }

    private void addOnlinePlayerCompletions(List<String> completions, String prefix) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(prefix.toLowerCase())) {
                completions.add(p.getName());
            }
        }
    }

    private interface BlockAction {
        void accept(Block block) throws Exception;
    }

    private enum SkipReason {
        NOT_PROTECTABLE,
        NO_PROTECTION,
        OWNER_MISMATCH,
        DUPLICATE_PROTECTION,
        TYPE_ALREADY_SET,
        TRANSFER_TARGET_SAME_AS_OWNER
    }

    private static final class OperationSummary {
        private long scanned;
        private long changed;
        private long created;
        private long transferred;
        private long removed;
        private long failed;
        private final Map<SkipReason, Long> skipped = new EnumMap<>(SkipReason.class);
        private final Set<UUID> processedProtections = new HashSet<>();

        private boolean markProcessed(Protection protection) {
            return processedProtections.add(protection.getId());
        }

        private void skip(SkipReason reason) {
            skipped.merge(reason, 1L, Long::sum);
        }

        private String describeMain() {
            return "走査 " + scanned +
                    ", 作成 " + created +
                    ", 変更 " + changed +
                    ", 移譲 " + transferred +
                    ", 削除 " + removed;
        }

        private String describeSkips() {
            if (skipped.isEmpty()) {
                return "";
            }
            List<String> parts = new ArrayList<>();
            for (Map.Entry<SkipReason, Long> entry : skipped.entrySet()) {
                parts.add(entry.getKey().name() + "=" + entry.getValue());
            }
            return String.join(", ", parts);
        }
    }

    private static final class AdminPlan {
        private final String worldName;
        private final BlockVector3 min;
        private final BlockVector3 max;
        private final List<Protection> protections;
        private final OperationSummary summary;
        private final long createdAt = System.currentTimeMillis();

        private AdminPlan(String worldName, BlockVector3 min, BlockVector3 max, List<Protection> protections, OperationSummary summary) {
            this.worldName = worldName;
            this.min = min;
            this.max = max;
            this.protections = protections;
            this.summary = summary;
        }
    }
}
