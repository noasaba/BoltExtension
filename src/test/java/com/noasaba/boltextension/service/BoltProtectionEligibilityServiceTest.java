package com.noasaba.boltextension.service;

import org.junit.jupiter.api.Test;
import org.popcraft.bolt.source.Source;
import org.popcraft.bolt.source.SourceTypes;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoltProtectionEligibilityServiceTest {

    @Test
    void createsCanonicalPlayerSource() {
        UUID playerId = UUID.fromString("ee9f1e5c-9f36-4e9a-8724-65f3afb25295");

        assertEquals("player:" + playerId, Source.player(playerId).toString());
    }

    @Test
    void createsCanonicalGroupSource() {
        assertEquals("group:builders", Source.of(SourceTypes.GROUP, "builders").toString());
    }
}
