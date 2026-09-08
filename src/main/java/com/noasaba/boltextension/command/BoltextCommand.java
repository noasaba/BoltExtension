package com.noasaba.boltextension.command;

import com.noasaba.boltextension.BoltExtension;
import com.noasaba.boltextension.config.PluginSettings;
import com.noasaba.boltextension.model.AccessDecision;
import com.noasaba.boltextension.model.AdminUnlockPlan;
import com.noasaba.boltextension.model.BoltInspection;
import com.noasaba.boltextension.model.InvalidProtection;
import com.noasaba.boltextension.model.OperationSummary;
import com.noasaba.boltextension.model.SelectionContext;
import com.noasaba.boltextension.service.BoltOperationService;
import com.noasaba.boltextension.service.EntityOperationService;
import com.noasaba.boltextension.service.WorldEditSelectionService;
import com.noasaba.boltextension.service.WorldGuardAccessService;
import com.sk89q.worldedit.IncompleteRegionException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class BoltextCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION_PRIVATE = "bolt.extension.private";
    public static final String PERMISSION_SET = "bolt.extension.set";
    public static final String PERMISSION_PUBLIC = "bolt.extension.public";
    public static final String PERMISSION_TRANSFER = "bolt.extension.transfer";
    public static final String PERMISSION_UNLOCK = "bolt.extension.unlock";
    public static final String PERMISSION_ACCESS_ADD = "bolt.extension.access.add";
    public static final String PERMISSION_ACCESS_REMOVE = "bolt.extension.access.remove";
    public static final String PERMISSION_ENTITY_SET = "bolt.extension.entity.set";
    public static final String PERMISSION_ENTITY_TRANSFER = "bolt.extension.entity.transfer";
    public static final String PERMISSION_ENTITY_UNLOCK = "bolt.extension.entity.unlock";
    public static final String PERMISSION_SCAN = "bolt.extension.scan";
    public static final String PERMISSION_INSPECT = "bolt.extension.inspect";
    public static final String PERMISSION_DEBUG = "bolt.extension.debug";
    public static final String PERMISSION_AUDIT = "bolt.extension.audit";
    public static final String PERMISSION_ADMIN_UNLOCK = "bolt.extension.admin.unlock";

    private final BoltExtension plugin;
    private final PluginSettings settings;
    private final WorldEditSelectionService selectionService;
    private final WorldGuardAccessService worldGuardService;
    private final BoltOperationService operationService;
    private final EntityOperationService entityOperationService;
    private final Map<UUID, AdminUnlockPlan> pendingAdminUnlocks = new HashMap<>();

    public BoltextCommand(
            BoltExtension plugin,
            PluginSettings settings,
            WorldEditSelectionService selectionService,
            WorldGuardAccessService worldGuardService,
            BoltOperationService operationService,
            EntityOperationService entityOperationService
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.selectionService = selectionService;
        this.worldGuardService = worldGuardService;
        this.operationService = operationService;
        this.entityOperationService = entityOperationService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "ゲーム内プレイヤーのみ使用できます");
            return true;
        }
        if (args.length == 0) {
            showUsage(player);
            return true;
        }

        String subcommand = normalize(args[0]);
        if ("user".equals(subcommand)) {
            args = args.clone();
            args[0] = "access";
            subcommand = "access";
        }
        try {
            return switch (subcommand) {
                case "inspect" -> handleInspect(player);
                case "debug" -> handleDebug(player, args);
                case "confirm" -> handleConfirm(player);
                case "audit" -> handleAudit(player, args);
                default -> handleSelectionCommand(player, subcommand, args);
            };
        } catch (IncompleteRegionException exception) {
            player.sendMessage(ChatColor.RED + "WorldEditの範囲選択が不完全です");
        } catch (Exception exception) {
            player.sendMessage(ChatColor.RED + "処理中にエラーが発生しました。サーバーログを確認してください");
            plugin.getLogger().log(Level.SEVERE, "boltextコマンド処理に失敗しました", exception);
        }
        return true;
    }

    private boolean handleSelectionCommand(Player player, String subcommand, String[] args)
            throws IncompleteRegionException {
        if (!hasCommandPermission(player, subcommand, args)) {
            return true;
        }
        if (!validateSelectionArguments(player, subcommand, args)) {
            return true;
        }

        SelectionContext selection = selectionService.selection(player);
        if (settings.maxVolume() > 0 && selection.volume() > settings.maxVolume()) {
            player.sendMessage(ChatColor.RED + String.format(
                    "選択範囲が大きすぎます（選択: %,d / 最大: %,d）",
                    selection.volume(),
                    settings.maxVolume()
            ));
            return true;
        }

        switch (subcommand) {
            case "public", "private" -> sendSummary(
                    player,
                    operationService.setProtection(player, selection, subcommand, true),
                    "更新"
            );
            case "set" -> sendSummary(player, operationService.setProtection(player, selection, normalize(args[1]), true), "更新");
            case "transfer" -> handleTransfer(player, selection, args, true);
            case "unlock" -> sendSummary(
                    player,
                    operationService.unlock(player, selection, false, true),
                    "削除"
            );
            case "access" -> handleAccess(player, selection, args, true);
            case "entity" -> handleEntity(player, selection, args, true);
            case "admin" -> handleAdminUnlock(player, selection, args);
            case "scan" -> handleScan(player, selection, args);
            default -> {
                player.sendMessage(ChatColor.RED + "不明なサブコマンドです: " + subcommand);
                showUsage(player);
            }
        }
        return true;
    }

    private boolean handleInspect(Player player) {
        if (!requirePermission(player, PERMISSION_INSPECT)) {
            return true;
        }
        Block block = player.getTargetBlockExact(100);
        if (block == null) {
            player.sendMessage(ChatColor.RED + "見ているブロックが見つかりません");
            return true;
        }

        BoltInspection inspection = operationService.inspect(player, block);
        AccessDecision worldGuard = worldGuardService.checkBlock(player, block);
        player.sendMessage(ChatColor.YELLOW + "BoltExtension inspect");
        player.sendMessage(ChatColor.GRAY + "block=" + block.getWorld().getName() + ":" +
                block.getX() + "," + block.getY() + "," + block.getZ() + " material=" + block.getType());
        player.sendMessage(ChatColor.GRAY + "protectable=" + inspection.protectable() +
                " protectedExact=" + inspection.protectedExact());
        player.sendMessage(ChatColor.GRAY + "exact=" +
                operationService.describeProtection(inspection.exactProtection()));
        player.sendMessage(ChatColor.GRAY + "matched=" +
                operationService.describeProtection(inspection.matchedProtection()));
        player.sendMessage(ChatColor.GRAY + "ownerMatch=" + inspection.ownerMatch() +
                " boltEditAccess=" + inspection.editAccess());
        player.sendMessage(ChatColor.GRAY + "worldGuard=" + worldGuard.describe());
        return true;
    }

    private boolean handleDebug(Player player, String[] args) {
        if (!requirePermission(player, PERMISSION_DEBUG)) {
            return true;
        }
        if (args.length > 1 && !"status".equalsIgnoreCase(args[1])) {
            player.sendMessage(ChatColor.RED + "使い方: /boltext debug status");
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "BoltExtension debug status");
        player.sendMessage(ChatColor.GRAY + "plugin=" + plugin.getDescription().getVersion() +
                " java=" + Runtime.version() + " server=" + Bukkit.getVersion());
        player.sendMessage(ChatColor.GRAY + "Bolt=" + pluginVersion("Bolt") +
                " WorldEdit=" + pluginVersion("WorldEdit") +
                " WorldGuard=" + pluginVersion("WorldGuard"));
        player.sendMessage(ChatColor.GRAY + "maxVolume=" + settings.maxVolume() +
                " confirmationTimeoutMs=" + settings.confirmationTimeoutMillis());
        player.sendMessage(ChatColor.GRAY + "worldGuardEnabled=" + settings.worldGuardEnabled() +
                " flagDefault=" + settings.worldGuardFlagDefault() +
                " allowNoRegion=" + settings.worldGuardAllowNoRegion() +
                " requireBuildAccess=" + settings.worldGuardRequireBuildAccess() +
                " requireMembership=" + settings.worldGuardRequireMembership() +
                " honorBypass=" + settings.worldGuardHonorBypassPermission());
        player.sendMessage(ChatColor.GRAY + "debugLogging=" + settings.debugLogging() +
                " maxErrorSamples=" + settings.maxErrorSamples());
        return true;
    }

    private void handleTransfer(Player player, SelectionContext selection, String[] args, boolean execute) {
        int targetIndex = execute ? 1 : 2;
        if (args.length <= targetIndex) {
            player.sendMessage(ChatColor.RED + (execute
                    ? "使い方: /boltext transfer <targetPlayer>"
                    : "使い方: /boltext scan transfer <targetPlayer>"));
            return;
        }
        UUID target = resolveKnownPlayer(args[targetIndex]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Boltに登録済みのプレイヤー名を指定してください");
            return;
        }
        sendSummary(
                player,
                operationService.transfer(player, selection, target, execute),
                execute ? "移譲" : "移譲予定"
        );
    }

    private void handleAdminUnlock(Player player, SelectionContext selection, String[] args) {
        pendingAdminUnlocks.remove(player.getUniqueId());
        AdminUnlockPlan plan = operationService.prepareAdminUnlock(player, selection);
        if (plan.preview().changedCount() == 0) {
            player.sendMessage(ChatColor.YELLOW + "削除対象の保護はありません");
            sendSummary(player, plan.preview(), "削除予定");
            return;
        }
        pendingAdminUnlocks.put(player.getUniqueId(), plan);
        player.sendMessage(ChatColor.YELLOW + "警告: ownerを問わず削除します。WorldGuardは削除時にも確認します。" +
                settings.confirmationTimeoutMillis() / 1_000L + "秒以内に /boltext confirm を実行してください");
        sendSummary(player, plan.preview(), "削除予定");
    }

    private boolean handleConfirm(Player player) {
        if (!requireOperationPermissions(player, PERMISSION_ADMIN_UNLOCK, "bolt.command.admin")) {
            return true;
        }
        AdminUnlockPlan plan = pendingAdminUnlocks.remove(player.getUniqueId());
        if (plan == null) {
            player.sendMessage(ChatColor.RED + "確認待ちのadmin unlockはありません");
            return true;
        }
        if (plan.expired(settings.confirmationTimeoutMillis())) {
            player.sendMessage(ChatColor.RED + "確認期限が切れました。/boltext admin unlock からやり直してください");
            return true;
        }

        try {
            SelectionContext current = selectionService.selection(player);
            if (!plan.selection().world().equals(current.world()) || !plan.selection().region().equals(current.region())) {
                player.sendMessage(ChatColor.RED + "WorldEdit選択範囲が変更されています。admin unlockからやり直してください");
                return true;
            }
        } catch (IncompleteRegionException exception) {
            player.sendMessage(ChatColor.RED + "WorldEditの範囲選択が不完全です。admin unlockからやり直してください");
            return true;
        }
        player.sendMessage(ChatColor.GRAY + "対象範囲: " + plan.selection().describe());
        sendSummary(player, operationService.executeAdminUnlock(player, plan), "削除");
        return true;
    }

    private void handleScan(Player player, SelectionContext selection, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "使い方: /boltext scan <public|private|set|transfer|unlock|access|entity|admin unlock>");
            return;
        }
        String target = normalize(args[1]);
        switch (target) {
            case "public", "private" -> sendSummary(
                    player,
                    operationService.setProtection(player, selection, target, false),
                    "更新予定"
            );
            case "set" -> sendSummary(player,
                    operationService.setProtection(player, selection, normalize(args[2]), false), "更新予定");
            case "transfer" -> handleTransfer(player, selection, args, false);
            case "unlock" -> sendSummary(
                    player,
                    operationService.unlock(player, selection, false, false),
                    "削除予定"
            );
            case "access" -> handleAccess(player, selection, sliceScanArguments(args), false);
            case "entity" -> handleEntity(player, selection, sliceScanArguments(args), false);
            case "admin" -> {
                if (args.length < 3 || !"unlock".equalsIgnoreCase(args[2])) {
                    player.sendMessage(ChatColor.RED + "使い方: /boltext scan admin unlock");
                    return;
                }
                sendSummary(
                        player,
                        operationService.unlock(player, selection, true, false),
                        "管理者削除予定"
                );
            }
            default -> player.sendMessage(ChatColor.RED + "不明なscan対象です: " + target);
        }
    }

    private boolean hasCommandPermission(Player player, String subcommand, String[] args) {
        return switch (subcommand) {
            case "public" -> requireOperationPermissions(player, PERMISSION_PUBLIC, "bolt.command.lock");
            case "private" -> requireOperationPermissions(player, PERMISSION_PRIVATE, "bolt.command.lock");
            case "set" -> requireOperationPermissions(player, PERMISSION_SET, "bolt.command.lock");
            case "transfer" -> requireOperationPermissions(player, PERMISSION_TRANSFER, "bolt.command.transfer");
            case "unlock" -> requireOperationPermissions(player, PERMISSION_UNLOCK, "bolt.command.unlock");
            case "access" -> accessPermission(player, args, false);
            case "entity" -> entityPermission(player, args, false);
            case "admin" -> requireOperationPermissions(player, PERMISSION_ADMIN_UNLOCK, "bolt.command.admin");
            case "scan" -> isAdminScan(args)
                    ? requireOperationPermissions(player, PERMISSION_ADMIN_UNLOCK, "bolt.command.admin")
                    : requirePermission(player, PERMISSION_SCAN) && scanTargetPermission(player, args);
            default -> true;
        };
    }

    private boolean validateSelectionArguments(Player player, String subcommand, String[] args) {
        return switch (subcommand) {
            case "public", "private", "unlock" -> true;
            case "set" -> validateType(player, args, 1, "/boltext set <protectionType>");
            case "transfer" -> validateTransferTarget(player, args, 1, "/boltext transfer <targetPlayer>");
            case "access" -> validateAccessArguments(player, args, 1, "/boltext access <add|remove|add-group|remove-group> <target> [accessType]");
            case "entity" -> validateEntityArguments(player, args, 1, "/boltext entity <set|public|private|transfer|unlock> [args...]");
            case "admin" -> {
                if (args.length < 2 || !"unlock".equalsIgnoreCase(args[1])) {
                    player.sendMessage(ChatColor.RED + "使い方: /boltext admin unlock");
                    yield false;
                }
                yield true;
            }
            case "scan" -> validateScanArguments(player, args);
            default -> {
                player.sendMessage(ChatColor.RED + "不明なサブコマンドです: " + subcommand);
                showUsage(player);
                yield false;
            }
        };
    }

    private boolean validateScanArguments(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED +
                    "使い方: /boltext scan <public|private|set|transfer|unlock|access|entity|admin unlock>");
            return false;
        }

        return switch (normalize(args[1])) {
            case "public", "private", "unlock" -> true;
            case "set" -> validateType(player, args, 2, "/boltext scan set <protectionType>");
            case "transfer" -> validateTransferTarget(
                    player,
                    args,
                    2,
                    "/boltext scan transfer <targetPlayer>"
            );
            case "access" -> validateAccessArguments(player, args, 2, "/boltext scan access <add|remove|add-group|remove-group> <target> [accessType]");
            case "entity" -> validateEntityArguments(player, args, 2, "/boltext scan entity <set|public|private|transfer|unlock> [args...]");
            case "admin" -> {
                if (args.length < 3 || !"unlock".equalsIgnoreCase(args[2])) {
                    player.sendMessage(ChatColor.RED + "使い方: /boltext scan admin unlock");
                    yield false;
                }
                yield true;
            }
            default -> {
                player.sendMessage(ChatColor.RED + "不明なscan対象です: " + args[1]);
                yield false;
            }
        };
    }

    private boolean validateTransferTarget(Player player, String[] args, int index, String usage) {
        if (args.length <= index) {
            player.sendMessage(ChatColor.RED + "使い方: " + usage);
            return false;
        }
        if (resolveKnownPlayer(args[index]) == null) {
            player.sendMessage(ChatColor.RED + "Boltに登録済みのプレイヤー名を指定してください");
            return false;
        }
        return true;
    }

    private boolean requirePermission(Player player, String permission) {
        if (player.hasPermission(permission)) {
            return true;
        }
        player.sendMessage(ChatColor.RED + "権限がありません: " + permission);
        return false;
    }

    private boolean requireOperationPermissions(Player player, String extensionPermission, String boltPermission) {
        return requirePermission(player, extensionPermission)
                && (!settings.requireBoltCommandPermissions() || requirePermission(player, boltPermission));
    }

    private boolean accessPermission(Player player, String[] args, boolean scan) {
        int actionIndex = scan ? 2 : 1;
        if (args.length <= actionIndex) {
            return true;
        }
        String permission = normalize(args[actionIndex]).startsWith("add")
                ? PERMISSION_ACCESS_ADD : PERMISSION_ACCESS_REMOVE;
        return requireOperationPermissions(player, permission, "bolt.command.edit");
    }

    private boolean scanTargetPermission(Player player, String[] args) {
        if (args.length < 2) {
            return true;
        }
        return switch (normalize(args[1])) {
            case "public" -> requireOperationPermissions(player, PERMISSION_PUBLIC, "bolt.command.lock");
            case "private" -> requireOperationPermissions(player, PERMISSION_PRIVATE, "bolt.command.lock");
            case "set" -> requireOperationPermissions(player, PERMISSION_SET, "bolt.command.lock");
            case "transfer" -> requireOperationPermissions(player, PERMISSION_TRANSFER, "bolt.command.transfer");
            case "unlock" -> requireOperationPermissions(player, PERMISSION_UNLOCK, "bolt.command.unlock");
            case "access" -> accessPermission(player, args, true);
            case "entity" -> entityPermission(player, args, true);
            default -> true;
        };
    }

    private boolean validateAccessArguments(Player player, String[] args, int actionIndex, String usage) {
        if (args.length <= actionIndex + 1) {
            player.sendMessage(ChatColor.RED + "使い方: " + usage);
            return false;
        }
        String action = normalize(args[actionIndex]);
        if (!List.of("add", "remove", "add-group", "remove-group").contains(action)) {
            player.sendMessage(ChatColor.RED + "使い方: " + usage);
            return false;
        }
        if (action.endsWith("group")) {
            if (operationService.resolveKnownGroup(args[actionIndex + 1]).isEmpty()) {
                player.sendMessage(ChatColor.RED + "Boltに登録済みのグループ名を指定してください");
                return false;
            }
            return true;
        }
        return validateTransferTarget(player, args, actionIndex + 1, usage);
    }

    private void handleAccess(Player player, SelectionContext selection, String[] args, boolean execute) {
        int actionIndex = 1;
        int targetIndex = actionIndex + 1;
        String action = normalize(args[actionIndex]);
        boolean add = action.startsWith("add");
        boolean group = action.endsWith("group");
        String type = add && args.length > targetIndex + 1
                ? normalize(args[targetIndex + 1]) : operationService.defaultAccessType();
        String source = group
                ? operationService.groupSource(args[targetIndex])
                : org.popcraft.bolt.source.Source.player(resolveKnownPlayer(args[targetIndex])).toString();
        sendSummary(player, operationService.changeAccessSource(player, selection, source, type, add, execute),
                execute ? "アクセス更新" : "アクセス更新予定");
    }

    private boolean validateType(Player player, String[] args, int index, String usage) {
        if (args.length <= index || !operationService.protectionTypes().contains(normalize(args[index]))) {
            player.sendMessage(ChatColor.RED + "Boltに登録済みのprotection typeを指定してください。使い方: " + usage);
            return false;
        }
        return true;
    }

    private boolean validateEntityArguments(Player player, String[] args, int actionIndex, String usage) {
        if (args.length <= actionIndex) {
            player.sendMessage(ChatColor.RED + "使い方: " + usage);
            return false;
        }
        return switch (normalize(args[actionIndex])) {
            case "public", "private" -> true;
            case "set" -> validateType(player, args, actionIndex + 1, usage);
            case "transfer" -> validateTransferTarget(player, args, actionIndex + 1, usage);
            case "unlock" -> true;
            default -> {
                player.sendMessage(ChatColor.RED + "使い方: " + usage);
                yield false;
            }
        };
    }

    private boolean entityPermission(Player player, String[] args, boolean scan) {
        int actionIndex = scan ? 2 : 1;
        if (args.length <= actionIndex) {
            return true;
        }
        return switch (normalize(args[actionIndex])) {
            case "public", "private", "set" -> requireOperationPermissions(player, PERMISSION_ENTITY_SET, "bolt.command.lock");
            case "transfer" -> requireOperationPermissions(player, PERMISSION_ENTITY_TRANSFER, "bolt.command.transfer");
            case "unlock" -> requireOperationPermissions(player, PERMISSION_ENTITY_UNLOCK, "bolt.command.unlock");
            default -> true;
        };
    }

    private void handleEntity(Player player, SelectionContext selection, String[] args, boolean execute) {
        String action = normalize(args[1]);
        switch (action) {
            case "public", "private" -> sendSummary(player,
                    entityOperationService.setProtection(player, selection, action, execute), execute ? "Entity更新" : "Entity更新予定");
            case "set" -> sendSummary(player,
                    entityOperationService.setProtection(player, selection, normalize(args[2]), execute), execute ? "Entity更新" : "Entity更新予定");
            case "transfer" -> sendSummary(player,
                    entityOperationService.transfer(player, selection, resolveKnownPlayer(args[2]), execute), execute ? "Entity移譲" : "Entity移譲予定");
            case "unlock" -> sendSummary(player,
                    entityOperationService.unlock(player, selection, execute), execute ? "Entity削除" : "Entity削除予定");
            default -> throw new IllegalArgumentException("Unsupported entity action: " + action);
        }
    }

    private String[] sliceScanArguments(String[] args) {
        String[] sliced = new String[args.length - 1];
        sliced[0] = args[1];
        System.arraycopy(args, 2, sliced, 1, args.length - 2);
        return sliced;
    }

    private UUID resolveKnownPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online.getUniqueId() : operationService.resolveKnownPlayer(name).orElse(null);
    }

    private boolean handleAudit(Player player, String[] args) {
        if (!requirePermission(player, PERMISSION_AUDIT)) {
            return true;
        }
        if (args.length < 2 || !"invalid".equalsIgnoreCase(args[1])) {
            player.sendMessage(ChatColor.RED + "使い方: /boltext audit invalid [page]");
            return true;
        }
        int page = args.length > 2 ? parsePage(args[2]) : 1;
        List<InvalidProtection> invalid = operationService.findInvalidProtections();
        int start = Math.min((page - 1) * 5, invalid.size());
        int end = Math.min(start + 5, invalid.size());
        player.sendMessage(ChatColor.YELLOW + "invalid Bolt protections: " + invalid.size() + " (page " + page + ")");
        for (InvalidProtection protection : invalid.subList(start, end)) {
            player.sendMessage(ChatColor.GRAY + protection.world() + ":" + protection.x() + "," + protection.y() + "," + protection.z() +
                    " material=" + protection.material() + " owner=" + protection.owner() + " type=" + protection.type());
        }
        return true;
    }

    private int parsePage(String value) {
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private boolean isAdminScan(String[] args) {
        return args.length >= 2 && "scan".equalsIgnoreCase(args[0]) &&
                "admin".equalsIgnoreCase(args[1]);
    }

    private void sendSummary(Player player, OperationSummary summary, String label) {
        player.sendMessage(ChatColor.GREEN + label + ": " + summary.describeMain());
        String skips = summary.describeSkips();
        if (!skips.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "スキップ: " + skips);
        }
        if (summary.failedCount() > 0) {
            player.sendMessage(ChatColor.RED + "失敗: " + summary.failedCount() + "件");
            if (player.hasPermission(PERMISSION_DEBUG)) {
                for (String sample : summary.errorSamples()) {
                    player.sendMessage(ChatColor.DARK_GRAY + sample);
                }
            }
        }
    }

    private void showUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW +
                "/boltext <public|private|set|transfer|unlock|access|entity|user|audit|inspect|scan|admin|confirm|debug>");
    }

    private String pluginVersion(String name) {
        Plugin dependency = Bukkit.getPluginManager().getPlugin(name);
        return dependency == null ? "missing" : dependency.getDescription().getVersion();
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> commands = new ArrayList<>();
            if (player.hasPermission(PERMISSION_PUBLIC)) commands.add("public");
            if (player.hasPermission(PERMISSION_PRIVATE)) commands.add("private");
            if (player.hasPermission(PERMISSION_SET)) commands.add("set");
            if (player.hasPermission(PERMISSION_TRANSFER)) commands.add("transfer");
            if (player.hasPermission(PERMISSION_UNLOCK)) commands.add("unlock");
            if (player.hasPermission(PERMISSION_ACCESS_ADD) || player.hasPermission(PERMISSION_ACCESS_REMOVE)) {
                commands.addAll(List.of("access", "user"));
            }
            if (player.hasPermission(PERMISSION_ENTITY_SET) || player.hasPermission(PERMISSION_ENTITY_TRANSFER)
                    || player.hasPermission(PERMISSION_ENTITY_UNLOCK)) commands.add("entity");
            if (player.hasPermission(PERMISSION_SCAN) || player.hasPermission(PERMISSION_ADMIN_UNLOCK)) {
                commands.add("scan");
            }
            if (player.hasPermission(PERMISSION_INSPECT)) commands.add("inspect");
            if (player.hasPermission(PERMISSION_AUDIT)) commands.add("audit");
            if (player.hasPermission(PERMISSION_ADMIN_UNLOCK)) {
                commands.add("admin");
                commands.add("confirm");
            }
            if (player.hasPermission(PERMISSION_DEBUG)) commands.add("debug");
            return filter(commands, args[0]);
        }
        if (args.length == 2) {
            return switch (normalize(args[0])) {
                case "transfer" -> player.hasPermission(PERMISSION_TRANSFER)
                        ? onlinePlayers(args[1])
                        : List.of();
                case "set" -> player.hasPermission(PERMISSION_SET) ? filter(operationService.protectionTypes(), args[1]) : List.of();
                case "entity" -> filter(entityActions(player), args[1]);
                case "admin" -> player.hasPermission(PERMISSION_ADMIN_UNLOCK)
                        ? filter(List.of("unlock"), args[1])
                        : List.of();
                case "debug" -> player.hasPermission(PERMISSION_DEBUG)
                        ? filter(List.of("status"), args[1])
                        : List.of();
                case "scan" -> filter(scanTargets(player), args[1]);
                case "access", "user" -> filter(List.of("add", "remove", "add-group", "remove-group"), args[1]);
                case "audit" -> player.hasPermission(PERMISSION_AUDIT) ? filter(List.of("invalid"), args[1]) : List.of();
                default -> List.of();
            };
        }
        if (args.length == 3 && "scan".equalsIgnoreCase(args[0])) {
            if ("transfer".equalsIgnoreCase(args[1]) && player.hasPermission(PERMISSION_SCAN)) {
                return onlinePlayers(args[2]);
            }
            if ("admin".equalsIgnoreCase(args[1]) && player.hasPermission(PERMISSION_ADMIN_UNLOCK)) {
                return filter(List.of("unlock"), args[2]);
            }
            if ("set".equalsIgnoreCase(args[1]) && player.hasPermission(PERMISSION_SET)) {
                return filter(operationService.protectionTypes(), args[2]);
            }
            if ("entity".equalsIgnoreCase(args[1])) {
                return filter(entityActions(player), args[2]);
            }
        }
        return List.of();
    }

    private List<String> scanTargets(Player player) {
        List<String> targets = new ArrayList<>();
        if (player.hasPermission(PERMISSION_SCAN)) {
            if (player.hasPermission(PERMISSION_PUBLIC)) targets.add("public");
            if (player.hasPermission(PERMISSION_PRIVATE)) targets.add("private");
            if (player.hasPermission(PERMISSION_SET)) targets.add("set");
            if (player.hasPermission(PERMISSION_TRANSFER)) targets.add("transfer");
            if (player.hasPermission(PERMISSION_UNLOCK)) targets.add("unlock");
            if (player.hasPermission(PERMISSION_ACCESS_ADD) || player.hasPermission(PERMISSION_ACCESS_REMOVE)) targets.add("access");
            if (player.hasPermission(PERMISSION_ENTITY_SET) || player.hasPermission(PERMISSION_ENTITY_TRANSFER)
                    || player.hasPermission(PERMISSION_ENTITY_UNLOCK)) targets.add("entity");
        }
        if (player.hasPermission(PERMISSION_ADMIN_UNLOCK)) {
            targets.add("admin");
        }
        return targets;
    }

    private List<String> entityActions(Player player) {
        List<String> actions = new ArrayList<>();
        if (player.hasPermission(PERMISSION_ENTITY_SET)) actions.addAll(List.of("set", "public", "private"));
        if (player.hasPermission(PERMISSION_ENTITY_TRANSFER)) actions.add("transfer");
        if (player.hasPermission(PERMISSION_ENTITY_UNLOCK)) actions.add("unlock");
        return actions;
    }

    private List<String> onlinePlayers(String prefix) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> normalize(name).startsWith(normalize(prefix)))
                .sorted()
                .toList();
    }

    private List<String> filter(List<String> candidates, String prefix) {
        String normalized = normalize(prefix);
        return candidates.stream()
                .filter(candidate -> candidate.startsWith(normalized))
                .sorted()
                .toList();
    }
}
