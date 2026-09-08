package com.noasaba.boltextension.model;

import java.util.List;

public record AccessDecision(boolean allowed, String reason, List<String> regions) {

    public static AccessDecision allow(String reason, List<String> regions) {
        return new AccessDecision(true, reason, List.copyOf(regions));
    }

    public static AccessDecision deny(String reason, List<String> regions) {
        return new AccessDecision(false, reason, List.copyOf(regions));
    }

    public String describe() {
        String regionText = regions.isEmpty() ? "none" : String.join(",", regions);
        return (allowed ? "ALLOW" : "DENY") + " reason=" + reason + " regions=" + regionText;
    }
}
