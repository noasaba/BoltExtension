package com.noasaba.boltextension.command;

import com.noasaba.boltextension.BoltExtension;
import com.noasaba.boltextension.config.PluginSettings;
import com.noasaba.boltextension.model.AccessDecision;
import com.noasaba.boltextension.model.AdminUnlockPlan;
import com.noasaba.boltextension.model.BoltInspection;
import com.noasaba.boltextension.model.OperationSummary;
import com.noasaba.boltextension.model.SelectionContext;
import com.noasaba.boltextension.service.BoltOperationService;
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

    public static final String PERMISSION_USE = "bolt.extension.use";
    public static final String PERMISSION_SCAN = "bolt.extension.scan";
    public static final String PERMISSION_INSPECT = "bolt.extension.inspect";
    public static final String PERMISSION_DEBUG = "bolt.extension.debug";
    public static final String PERMISSION_ADMIN = "bolt.extension.admin";

    private final BoltExtension plugin;
    private final PluginSettings settings;
    private final WorldEditSelectionService selectionService;
    private final WorldGuardAccessService worldGuardService;
    private final BoltOperationService operationService;
    private final Map<UUID, AdminUnlockPlan> pendingAdminUnlocks = new HashMap<>();

    public BoltextCommand(
            BoltExtension plugin,
            PluginSettings settings,
            WorldEditSelectionService selectionService,
            WorldGuardAccessService worldGuardService,
            BoltOperationService operationService
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.selectionService = selectionService;
        this.worldGuardService = worldGuardService;
        this.operationService = operationService;
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
        try {
            return switch (subcommand) {
                case "inspect" -> handleInspect(player);
                case "debug" -> handleDebug(player, args);
                case "confirm" -> handleConfirm(player);
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

        boolean adminBypass = isAdminUnlock(args);
        if (!adminBypass) {
            AccessDecision access = worldGuardService.checkSelection(player, selection);
            if (!access.allowed()) {
                player.sendMessage(ChatColor.RED + "WorldGuardによりこの範囲での操作が拒否されました");
                if (player.hasPermission(PERMISSION_DEBUG)) {
                    player.sendMessage(ChatColor.GRAY + access.describe());
                }
                return true;
            }
        }

        switch (subcommand) {
            case "public", "private" -> sendSummary(
                    player,
                    operationService.setProtection(player, selection, subcommand, true),
                    "更新"
            );
            case "transfer" -> handleTransfer(player, selection, args, true);
            case "unlock" -> sendSummary(
                    player,
                    operationService.unlock(player, selection, false, true),
                    "削除"
            );
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
        Player target = Bukkit.getPlayerExact(args[targetIndex]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "指定されたプレイヤーはオンラインではありません");
            return;
        }
        sendSummary(
                player,
                operationService.transfer(player, selection, target.getUniqueId(), execute),
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
        player.sendMessage(ChatColor.YELLOW + "警告: ownerとWorldGuardを無視して削除します。" +
                settings.confirmationTimeoutMillis() / 1_000L + "秒以内に /boltext confirm を実行してください");
        sendSummary(player, plan.preview(), "削除予定");
    }

    private boolean handleConfirm(Player player) {
        if (!requirePermission(player, PERMISSION_ADMIN)) {
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

        player.sendMessage(ChatColor.GRAY + "対象範囲: " + plan.selection().describe());
        sendSummary(player, operationService.executeAdminUnlock(plan), "削除");
        return true;
    }

    private void handleScan(Player player, SelectionContext selection, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "使い方: /boltext scan <public|private|transfer|unlock|admin unlock>");
            return;
        }
        String target = normalize(args[1]);
        switch (target) {
            case "public", "private" -> sendSummary(
                    player,
                    operationService.setProtection(player, selection, target, false),
                    "更新予定"
            );
            case "transfer" -> handleTransfer(player, selection, args, false);
            case "unlock" -> sendSummary(
                    player,
                    operationService.unlock(player, selection, false, false),
                    "削除予定"
            );
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
            case "public", "private", "transfer", "unlock" -> requirePermission(player, PERMISSION_USE);
            case "admin" -> requirePermission(player, PERMISSION_ADMIN);
            case "scan" -> isAdminScan(args)
                    ? requirePermission(player, PERMISSION_ADMIN)
                    : requirePermission(player, PERMISSION_SCAN);
            default -> true;
        };
    }

    private boolean validateSelectionArguments(Player player, String subcommand, String[] args) {
        return switch (subcommand) {
            case "public", "private", "unlock" -> true;
            case "transfer" -> validateTransferTarget(player, args, 1, "/boltext transfer <targetPlayer>");
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
                    "使い方: /boltext scan <public|private|transfer|unlock|admin unlock>");
            return false;
        }

        return switch (normalize(args[1])) {
            case "public", "private", "unlock" -> true;
            case "transfer" -> validateTransferTarget(
                    player,
                    args,
                    2,
                    "/boltext scan transfer <targetPlayer>"
            );
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
        if (Bukkit.getPlayerExact(args[index]) == null) {
            player.sendMessage(ChatColor.RED + "指定されたプレイヤーはオンラインではありません");
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

    private boolean isAdminUnlock(String[] args) {
        return (args.length >= 2 && "admin".equalsIgnoreCase(args[0]) && "unlock".equalsIgnoreCase(args[1])) ||
                (args.length >= 3 && "scan".equalsIgnoreCase(args[0]) &&
                        "admin".equalsIgnoreCase(args[1]) && "unlock".equalsIgnoreCase(args[2]));
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
                "/boltext <public|private|transfer|unlock|inspect|scan|admin|confirm|debug>");
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
            if (player.hasPermission(PERMISSION_USE)) {
                commands.addAll(List.of("public", "private", "transfer", "unlock"));
            }
            if (player.hasPermission(PERMISSION_SCAN) || player.hasPermission(PERMISSION_ADMIN)) {
                commands.add("scan");
            }
            if (player.hasPermission(PERMISSION_INSPECT)) commands.add("inspect");
            if (player.hasPermission(PERMISSION_ADMIN)) {
                commands.add("admin");
                commands.add("confirm");
            }
            if (player.hasPermission(PERMISSION_DEBUG)) commands.add("debug");
            return filter(commands, args[0]);
        }
        if (args.length == 2) {
            return switch (normalize(args[0])) {
                case "transfer" -> player.hasPermission(PERMISSION_USE)
                        ? onlinePlayers(args[1])
                        : List.of();
                case "admin" -> player.hasPermission(PERMISSION_ADMIN)
                        ? filter(List.of("unlock"), args[1])
                        : List.of();
                case "debug" -> player.hasPermission(PERMISSION_DEBUG)
                        ? filter(List.of("status"), args[1])
                        : List.of();
                case "scan" -> filter(scanTargets(player), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && "scan".equalsIgnoreCase(args[0])) {
            if ("transfer".equalsIgnoreCase(args[1]) && player.hasPermission(PERMISSION_SCAN)) {
                return onlinePlayers(args[2]);
            }
            if ("admin".equalsIgnoreCase(args[1]) && player.hasPermission(PERMISSION_ADMIN)) {
                return filter(List.of("unlock"), args[2]);
            }
        }
        return List.of();
    }

    private List<String> scanTargets(Player player) {
        List<String> targets = new ArrayList<>();
        if (player.hasPermission(PERMISSION_SCAN)) {
            targets.addAll(List.of("public", "private", "transfer", "unlock"));
        }
        if (player.hasPermission(PERMISSION_ADMIN)) {
            targets.add("admin");
        }
        return targets;
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
