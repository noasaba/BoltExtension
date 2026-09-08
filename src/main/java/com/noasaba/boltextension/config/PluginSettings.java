package com.noasaba.boltextension.config;

import org.bukkit.configuration.file.FileConfiguration;

public record PluginSettings(
        long maxVolume,
        long confirmationTimeoutMillis,
        boolean worldGuardEnabled,
        boolean worldGuardFlagDefault,
        boolean worldGuardAllowNoRegion,
        boolean worldGuardRequireBuildAccess,
        boolean worldGuardRequireMembership,
        boolean worldGuardHonorBypassPermission,
        boolean debugLogging,
        int maxErrorSamples
) {

    public static PluginSettings from(FileConfiguration config) {
        long confirmationSeconds = Math.max(1L, config.getLong("confirmation-timeout-seconds", 60L));
        int maxErrors = Math.max(0, config.getInt("logging.max-errors", 10));
        return new PluginSettings(
                config.getLong("max-volume", 1_000_000L),
                toMillis(confirmationSeconds),
                config.getBoolean("worldguard.enabled", true),
                config.getBoolean("worldguard.flag-default", true),
                config.getBoolean("worldguard.allow-no-region", false),
                config.getBoolean("worldguard.require-build-access", true),
                config.getBoolean("worldguard.require-membership", true),
                config.getBoolean("worldguard.honor-bypass-permission", true),
                config.getBoolean("logging.debug", false),
                maxErrors
        );
    }

    private static long toMillis(long seconds) {
        return seconds > Long.MAX_VALUE / 1_000L ? Long.MAX_VALUE : seconds * 1_000L;
    }
}
