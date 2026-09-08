package com.noasaba.boltextension.model;

import org.popcraft.bolt.protection.Protection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class OperationSummary {

    private final String operationId = UUID.randomUUID().toString().substring(0, 8);
    private final Map<SkipReason, Long> skipped = new EnumMap<>(SkipReason.class);
    private final Set<UUID> processedProtections = new HashSet<>();
    private final List<String> errorSamples = new ArrayList<>();

    private long scanned;
    private long created;
    private long changed;
    private long transferred;
    private long removed;
    private long failed;

    public String operationId() {
        return operationId;
    }

    public void scanned() {
        scanned++;
    }

    public void created() {
        created++;
    }

    public void changed() {
        changed++;
    }

    public void transferred() {
        transferred++;
    }

    public void removed() {
        removed++;
    }

    public void failed(String sample, int maxSamples) {
        failed++;
        if (errorSamples.size() < maxSamples) {
            errorSamples.add(sample);
        }
    }

    public void skip(SkipReason reason) {
        skipped.merge(reason, 1L, Long::sum);
    }

    public boolean markProcessed(Protection protection) {
        return processedProtections.add(protection.getId());
    }

    public long changedCount() {
        return created + changed + transferred + removed;
    }

    public long failedCount() {
        return failed;
    }

    public List<String> errorSamples() {
        return List.copyOf(errorSamples);
    }

    public String describeMain() {
        return "ID " + operationId +
                " | 走査 " + scanned +
                ", 作成 " + created +
                ", 変更 " + changed +
                ", 移譲 " + transferred +
                ", 削除 " + removed;
    }

    public String describeSkips() {
        if (skipped.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<SkipReason, Long> entry : skipped.entrySet()) {
            parts.add(entry.getKey().name() + "=" + entry.getValue());
        }
        return String.join(", ", parts);
    }
}
