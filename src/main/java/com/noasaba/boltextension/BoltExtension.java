package com.noasaba.boltextension;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BoltExtension extends JavaPlugin implements CommandExecutor, TabCompleter {

    private BoltAPI bolt;
    private int maxVolumeThreshold;

    // 管理者向け全削除の確認フラグを保持するマップ
    private Map<UUID, Boolean> adminConfirmMap = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        maxVolumeThreshold = getConfig().getInt("max-volume", 1000000);

        if (Bukkit.getServer().getPluginManager().getPlugin("WorldEdit") == null) {
            getLogger().severe("WorldEdit が見つかりません。プラグインを無効化します。");
            Bukkit.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.bolt = Bukkit.getServer().getServicesManager().load(BoltAPI.class);
        if (this.bolt == null) {
            getLogger().severe("BoltAPI が取得できません。Bolt プラグインが導入されているか確認してください。");
            Bukkit.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getCommand("boltext").setExecutor(this);
        getCommand("boltext").setTabCompleter(this);
        getLogger().info("== === ==\nBoltExtension v " + getDescription().getVersion() + " Developed by NOASABA (by nanosize)\n== === ==");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // サブコマンド例：
        // /boltext public
        // /boltext private
        // /boltext transfer <targetPlayer>
        // /boltext unlock
        // /boltext admin unlock
        // /boltext confirm

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはゲーム内からのみ実行できます。");
            return true;
        }
        Player player = (Player) sender;

        // WorldEdit の選択範囲取得
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
        Region region;
        try {
            region = session.getSelection(BukkitAdapter.adapt(player.getWorld()));
        } catch (IncompleteRegionException e) {
            player.sendMessage(ChatColor.RED + "選択範囲が不完全です。WorldEdit で範囲を選択してください。");
            return true;
        }

        // 選択範囲のボリューム計算
        int minX = region.getMinimumPoint().getX();
        int minY = region.getMinimumPoint().getY();
        int minZ = region.getMinimumPoint().getZ();
        int maxX = region.getMaximumPoint().getX();
        int maxY = region.getMaximumPoint().getY();
        int maxZ = region.getMaximumPoint().getZ();
        int volume = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > maxVolumeThreshold) {
            player.sendMessage(ChatColor.RED + "選択範囲が大きすぎます（" + volume + " ブロック）。処理を中断しました。");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.YELLOW + "使い方: /boltext <public|private|transfer|unlock|admin|confirm> [args...]");
            return true;
        }

        String subCommand = args[0].toLowerCase();
        int count = 0;

        try {
            switch (subCommand) {
                case "public":
                case "private": {
                    // 保護の種類更新／新規作成（自分所有のみ）
                    String newType = subCommand; // "public" または "private"
                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                Location loc = new Location(player.getWorld(), x, y, z);
                                Block block = loc.getBlock();
                                BlockProtection protection = bolt.loadProtection(block);
                                if (protection != null) {
                                    if (!protection.getOwner().equals(player.getUniqueId())) continue;
                                    if (!protection.getType().equals(newType)) {
                                        protection.setType(newType);
                                        bolt.saveProtection(protection);
                                        count++;
                                    }
                                } else {
                                    BlockProtection newProtection = bolt.createProtection(block, player.getUniqueId(), newType);
                                    bolt.saveProtection(newProtection);
                                    count++;
                                }
                            }
                        }
                    }
                    break;
                }
                case "transfer": {
                    // /boltext transfer <targetPlayer>
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "使い方: /boltext transfer <targetPlayer>");
                        return true;
                    }
                    String targetName = args[1];
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target == null) {
                        player.sendMessage(ChatColor.RED + "指定されたプレイヤーはオンラインではありません。");
                        return true;
                    }
                    UUID targetUUID = target.getUniqueId();
                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
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
                    break;
                }
                case "unlock": {
                    // /boltext unlock : 自分所有の保護のみ削除
                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
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
                    break;
                }
                case "admin": {
                    // /boltext admin unlock : 管理者が他人の保護も含めて削除するための警告を表示し、確認状態にする
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "使い方: /boltext admin unlock");
                        return true;
                    }
                    String adminAction = args[1].toLowerCase();
                    if ("unlock".equals(adminAction)) {
                        if (!player.hasPermission("bolt.extension.admin")) {
                            player.sendMessage(ChatColor.RED + "あなたは管理者権限を持っていません。");
                            return true;
                        }
                        adminConfirmMap.put(player.getUniqueId(), true);
                        player.sendMessage(ChatColor.YELLOW + "警告: 他人の保護も削除されます。本当に実行する場合は /boltext confirm と入力してください。");
                    } else {
                        player.sendMessage(ChatColor.RED + "不明な admin 操作: " + adminAction);
                    }
                    break;
                }
                case "confirm": {
                    // /boltext confirm : admin unlock で設定された確認状態なら実行
                    if (!adminConfirmMap.getOrDefault(player.getUniqueId(), false)) {
                        player.sendMessage(ChatColor.RED + "確認状態ではありません。");
                        return true;
                    }
                    // 選択範囲内のすべての保護を削除（所有者に関係なく）
                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
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
                    // 確認状態を解除
                    adminConfirmMap.remove(player.getUniqueId());
                    break;
                }
                default:
                    player.sendMessage(ChatColor.RED + "不明なサブコマンド: " + subCommand);
                    return true;
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "エラーが発生しました: " + e.getMessage());
            e.printStackTrace();
            return true;
        }

        player.sendMessage(ChatColor.GREEN + "更新したブロック数: " + count);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String[] subs = {"public", "private", "unlock", "transfer", "admin", "confirm"};
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) {
                    completions.add(s);
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("admin".equals(sub)) {
                if ("unlock".startsWith(args[1].toLowerCase()))
                    completions.add("unlock");
            } else if ("transfer".equals(sub)) {
                // オンラインプレイヤー名の候補
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase()))
                        completions.add(p.getName());
                }
            } else if ("trust".equals(sub)) {
                // trust は削除しました
            }
        }
        return completions;
    }
}
