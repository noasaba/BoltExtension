package com.noasaba.boltextension.model;

import java.util.UUID;

public record InvalidProtection(
        String world,
        int x,
        int y,
        int z,
        UUID owner,
        String type,
        String material
) {
}
