package com.noasaba.boltextension.model;

import org.popcraft.bolt.protection.Protection;

import java.util.List;

public record AdminUnlockPlan(
        SelectionContext selection,
        List<Protection> protections,
        OperationSummary preview,
        long createdAt
) {

    public AdminUnlockPlan(SelectionContext selection, List<Protection> protections, OperationSummary preview) {
        this(selection, List.copyOf(protections), preview, System.currentTimeMillis());
    }

    public boolean expired(long timeoutMillis) {
        return System.currentTimeMillis() - createdAt > timeoutMillis;
    }
}
