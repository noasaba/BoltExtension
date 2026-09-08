package com.noasaba.boltextension;

import com.noasaba.boltextension.command.BoltextCommand;
import com.noasaba.boltextension.config.PluginSettings;
import com.noasaba.boltextension.service.BoltOperationService;
import com.noasaba.boltextension.service.BoltProtectionEligibilityService;
import com.noasaba.boltextension.service.EntityOperationService;
import com.noasaba.boltextension.service.WorldEditSelectionService;
import com.noasaba.boltextension.service.WorldGuardAccessService;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.popcraft.bolt.BoltAPI;
import org.popcraft.bolt.BoltPlugin;

import java.util.logging.Level;

public final class BoltExtension extends JavaPlugin {

    public static final String WORLDGUARD_FLAG_NAME = "bolt-extension-allow";

    private StateFlag worldGuardFlag;
    private String loadFailure;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        reloadConfig();

        PluginSettings settings = PluginSettings.from(getConfig());
        if (!settings.worldGuardEnabled()) {
            return;
        }

        try {
            worldGuardFlag = registerOrReuseWorldGuardFlag(settings.worldGuardFlagDefault());
        } catch (RuntimeException exception) {
            loadFailure = exception.getMessage();
            getLogger().log(Level.SEVERE, "WorldGuardフラグを初期化できません", exception);
        }
    }

    @Override
    public void onEnable() {
        if (loadFailure != null) {
            disable("起動前の初期化に失敗しました: " + loadFailure);
            return;
        }

        reloadConfig();
        PluginSettings settings = PluginSettings.from(getConfig());
        if (settings.worldGuardEnabled() && worldGuardFlag == null) {
            disable("WorldGuard連携が有効ですが、カスタムフラグを利用できません");
            return;
        }

        BoltAPI boltApi = Bukkit.getServicesManager().load(BoltAPI.class);
        if (boltApi == null) {
            disable("BoltAPIサービスが見つかりません");
            return;
        }
        if (!(boltApi instanceof BoltPlugin boltPlugin)) {
            disable("BoltAPIの実装がBoltPluginではないため、保護可否を安全に判定できません");
            return;
        }

        PluginCommand command = getCommand("boltext");
        if (command == null) {
            disable("plugin.yml に boltext コマンドが定義されていません");
            return;
        }

        WorldEditSelectionService selectionService = new WorldEditSelectionService();
        WorldGuardAccessService worldGuardService = new WorldGuardAccessService(
                settings,
                worldGuardFlag,
                getLogger()
        );
        BoltProtectionEligibilityService eligibilityService = new BoltProtectionEligibilityService(boltPlugin);
        BoltOperationService operationService = new BoltOperationService(
                boltApi,
                settings,
                worldGuardService,
                eligibilityService,
                getLogger()
        );
        EntityOperationService entityOperationService = new EntityOperationService(
                boltApi,
                settings,
                worldGuardService,
                eligibilityService,
                getLogger()
        );
        BoltextCommand commandHandler = new BoltextCommand(
                this,
                settings,
                selectionService,
                worldGuardService,
                operationService,
                entityOperationService
        );

        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
        getLogger().info(getDescription().getName() + " v" + getDescription().getVersion() + " enabled");
        getLogger().info("WorldGuard integration: " + (settings.worldGuardEnabled() ? "enabled" : "disabled"));
        getLogger().info("Debug logging: " + (settings.debugLogging() ? "enabled" : "disabled"));
    }

    private StateFlag registerOrReuseWorldGuardFlag(boolean defaultValue) {
        var registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag flag = new StateFlag(WORLDGUARD_FLAG_NAME, defaultValue);
            registry.register(flag);
            return flag;
        } catch (FlagConflictException conflict) {
            Flag<?> existing = registry.get(WORLDGUARD_FLAG_NAME);
            if (existing instanceof StateFlag stateFlag) {
                getLogger().info("既存のWorldGuardフラグを再利用します: " + WORLDGUARD_FLAG_NAME);
                return stateFlag;
            }
            throw new IllegalStateException(
                    "同名のWorldGuardフラグがStateFlag以外の型で登録されています: " + WORLDGUARD_FLAG_NAME,
                    conflict
            );
        }
    }

    private void disable(String reason) {
        getLogger().severe(reason);
        Bukkit.getPluginManager().disablePlugin(this);
    }
}
