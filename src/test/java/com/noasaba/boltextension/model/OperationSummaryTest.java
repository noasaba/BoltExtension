package com.noasaba.boltextension.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationSummaryTest {

    @Test
    void reportsPotentialCreationSeparatelyFromConfirmedCreation() {
        OperationSummary summary = new OperationSummary();
        summary.created();
        summary.potentialCreated();

        assertTrue(summary.describeMain().contains("作成 1"));
        assertTrue(summary.describeMain().contains("作成見込み 1"));
    }

    @Test
    void mergesBlockAndEntityResults() {
        OperationSummary blocks = new OperationSummary();
        blocks.created();
        OperationSummary entities = new OperationSummary();
        entities.changed();

        blocks.mergeFrom(entities, 10);

        assertTrue(blocks.describeMain().contains("作成 1"));
        assertTrue(blocks.describeMain().contains("変更 1"));
    }
}
