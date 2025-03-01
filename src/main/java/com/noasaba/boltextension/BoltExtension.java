package com.noasaba.boltextension;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
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

import java.util.*;

public class BoltExtension extends JavaPlugin implements CommandExecutor, TabCompleter {

    private BoltAPI bolt;
    private int maxVolumeThreshold;
    private final Map<UUID, Boolean> adminConfirmMap = new HashMap<>();
    private static StateFlag BOLT_EXTENSION_FLAG;
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
        maxVolumeThreshold = getConfig().getInt("max-volume", 1000000);

        // 依存関係チェック：WorldEdit
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
            getLogger().severe("WorldEditが見つかりません");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // BoltAPI の取得
        this.bolt = Bukkit.getServicesManager().load(BoltAPI.class);
        if (this.bolt == null) {
            getLogger().severe("BoltAPIが見つかりません");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // WorldGuard の状態再確認
        if (wgEnabled && WorldGuardPlugin.inst() == null) {
            getLogger().warning("WorldGuardが無効です");
            wgEnabled = false;
        }

        getCommand("boltext").setExecutor(this);
        getCommand("boltext").setTabCompleter(this);
        //getLogger().info("BoltExtension v" + getDescription().getVersion() + " 起動完了");
        getLogger().info("== === ==");
        getLogger().info(getDescription().getName() + " v" + getDescription().getVersion() );
        getLogger().info(" Developed by NOASABA (by nanosize)");
        getLogger().info("== === ==");
    }

    /**
     * 選択範囲に対する WorldGuard 権限チェック
     * 代表となる8点の座標（min.x(), min.y(), min.z() など）でチェックします。
     */
    private boolean checkWorldGuardAccess(Player player, Region selection) {
        if (!wgEnabled) return true;

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            BlockVector3 min = selection.getMinimumPoint();
            BlockVector3 max = selection.getMaximumPoint();

            return checkPoint(player, query, min.x(), min.y(), min.z()) ||
                    checkPoint(player, query, max.x(), min.y(), min.z()) ||
                    checkPoint(player, query, min.x(), max.y(), min.z()) ||
                    checkPoint(player, query, max.x(), max.y(), min.z()) ||
                    checkPoint(player, query, min.x(), min.y(), max.z()) ||
                    checkPoint(player, query, max.x(), min.y(), max.z()) ||
                    checkPoint(player, query, min.x(), max.y(), max.z()) ||
                    checkPoint(player, query, max.x(), max.y(), max.z());
        } catch (Exception e) {
            getLogger().warning("権限チェックエラー: " + e.getMessage());
            return false;
        }
    }

    /**
     * 指定された座標における権限チェック
     */
    private boolean checkPoint(Player player, RegionQuery query, int x, int y, int z) {
        Location loc = new Location(player.getWorld(), x, y, z);
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

    /**
     * ProtectedRegion に対するアクセス判定
     * まずフラグチェックを行い、フラグが DENY なら即時拒否し、
     * その後オーナー/メンバーチェックを実施する。
     */
    private boolean hasRegionAccess(Player player, ProtectedRegion region) {
        // フラグチェックを最初に行う
        StateFlag.State flagState = region.getFlag(BOLT_EXTENSION_FLAG);
        boolean flagAllowed = (flagState == null) ?
                getConfig().getBoolean("worldguard.flag-default", true) :
                (flagState == StateFlag.State.ALLOW);

        // フラグが DENY の場合は即時拒否
        if (!flagAllowed) {
            return false;
        }

        // オーナー/メンバーチェック
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
        try {
            SessionManager sessionManager = WorldEdit.getInstance().getSessionManager();
            Region selection = sessionManager.get(BukkitAdapter.adapt(player))
                    .getSelection(BukkitAdapter.adapt(player.getWorld()));

            // 最初に WorldGuard の権限チェック（.x(), .y(), .z() を使用）
            if (!checkWorldGuardAccess(player, selection)) {
                player.sendMessage(ChatColor.RED + "この領域での操作権限がありません");
                return true;
            }

            // ボリューム計算（.x(), .y(), .z() を使用）
            BlockVector3 min = selection.getMinimumPoint();
            BlockVector3 max = selection.getMaximumPoint();
            int volume = (max.x() - min.x() + 1) *
                    (max.y() - min.y() + 1) *
                    (max.z() - min.z() + 1);
            if (volume > maxVolumeThreshold) {
                player.sendMessage(ChatColor.RED + String.format(
                        "選択範囲が大きすぎます（最大許容: %,d ブロック）", maxVolumeThreshold));
                return true;
            }

            // サブコマンド処理
            return processCommand(player, selection, args);

        } catch (IncompleteRegionException e) {
            player.sendMessage(ChatColor.RED + "範囲選択が不完全です");
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "エラーが発生しました");
            e.printStackTrace();
        }
        return false;
    }

    private boolean processCommand(Player player, Region region, String[] args) {
        if (args.length < 1) {
            showUsage(player);
            return false;
        }
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "public":
            case "private":
                handleProtection(player, region, subCommand);
                break;
            case "transfer":
                handleTransfer(player, region, args);
                break;
            case "unlock":
                handleUnlock(player, region);
                break;
            case "admin":
                handleAdmin(player, args);
                break;
            case "confirm":
                handleConfirm(player, region);
                break;
            default:
                player.sendMessage(ChatColor.RED + "不明なサブコマンド: " + subCommand);
        }
        return true;
    }

    private void showUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "使い方: /boltext <public|private|transfer|unlock|admin|confirm> [args...]");
    }

    // 各サブコマンド処理メソッドの実装例

    private void handleProtection(Player player, Region region, String type) {
        int count = 0;
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    Location loc = new Location(player.getWorld(), x, y, z);
                    Block block = loc.getBlock();
                    BlockProtection protection = bolt.loadProtection(block);
                    if (protection != null) {
                        if (!protection.getOwner().equals(player.getUniqueId()))
                            continue;
                        if (!protection.getType().equals(type)) {
                            protection.setType(type);
                            bolt.saveProtection(protection);
                            count++;
                        }
                    } else {
                        BlockProtection newProtection = bolt.createProtection(block, player.getUniqueId(), type);
                        bolt.saveProtection(newProtection);
                        count++;
                    }
                }
            }
        }
        player.sendMessage(ChatColor.GREEN + "更新したブロック数: " + count);
    }

    private void handleTransfer(Player player, Region region, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "使い方: /boltext transfer <targetPlayer>");
            return;
        }
        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "指定されたプレイヤーはオンラインではありません");
            return;
        }
        int count = 0;
        UUID targetUUID = target.getUniqueId();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    Location loc = new Location(player.getWorld(), x, y, z);
                    Block block = loc.getBlock();
                    BlockProtection protection = bolt.loadProtection(block);
                    if (protection != null && protection.getOwner().equals(player.getUniqueId())) {
                        if (!protection.getOwner().equals(targetUUID)) {
                            protection.setOwner(targetUUID);
                            bolt.saveProtection(protection);
                            count++;
                        }
                    }
                }
            }
        }
        player.sendMessage(ChatColor.GREEN + "移譲したブロック数: " + count);
    }

    private void handleUnlock(Player player, Region region) {
        int count = 0;
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    Location loc = new Location(player.getWorld(), x, y, z);
                    Block block = loc.getBlock();
                    BlockProtection protection = bolt.loadProtection(block);
                    if (protection != null && protection.getOwner().equals(player.getUniqueId())) {
                        bolt.removeProtection(protection);
                        count++;
                    }
                }
            }
        }
        player.sendMessage(ChatColor.GREEN + "削除したブロック数: " + count);
    }

    private void handleAdmin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "使い方: /boltext admin unlock");
            return;
        }
        String adminAction = args[1].toLowerCase();
        if ("unlock".equals(adminAction)) {
            if (!player.hasPermission("bolt.extension.admin")) {
                player.sendMessage(ChatColor.RED + "あなたは管理者権限を持っていません");
                return;
            }
            adminConfirmMap.put(player.getUniqueId(), true);
            player.sendMessage(ChatColor.YELLOW + "警告: 他人の保護も削除されます。本当に実行する場合は /boltext confirm と入力してください");
        } else {
            player.sendMessage(ChatColor.RED + "不明な admin 操作: " + adminAction);
        }
    }

    private void handleConfirm(Player player, Region region) {
        if (!adminConfirmMap.getOrDefault(player.getUniqueId(), false)) {
            player.sendMessage(ChatColor.RED + "確認状態ではありません");
            return;
        }
        int count = 0;
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    Location loc = new Location(player.getWorld(), x, y, z);
                    Block block = loc.getBlock();
                    BlockProtection protection = bolt.loadProtection(block);
                    if (protection != null) {
                        bolt.removeProtection(protection);
                        count++;
                    }
                }
            }
        }
        adminConfirmMap.remove(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "削除したブロック数: " + count);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // メインコマンドの補完
            List<String> subCommands = Arrays.asList("public", "private", "transfer", "unlock", "admin", "confirm");
            for (String sub : subCommands) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            // サブコマンド別の補完
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "transfer":
                    // オンラインプレイヤー名を補完
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(p.getName());
                        }
                    }
                    break;
                case "admin":
                    if ("unlock".startsWith(args[1].toLowerCase())) {
                        completions.add("unlock");
                    }
                    break;
            }
        }
        return completions;
    }
}
