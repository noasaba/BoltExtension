package com.noasaba.boltextension.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import org.slf4j.Logger;

public final class VelocityBoltExtension {

    private final Logger logger;

    @Inject
    public VelocityBoltExtension(Logger logger) {
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("BoltExtension Velocity companion loaded. Install the Paper jar on backend servers for block protection commands.");
    }
}
