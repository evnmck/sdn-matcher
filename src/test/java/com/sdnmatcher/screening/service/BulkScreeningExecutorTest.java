package com.sdnmatcher.screening.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BulkScreeningExecutorTest {
    @Test
    void mapsEveryItemAndPreservesInputOrder() {
        BulkScreeningExecutor executor = new BulkScreeningExecutor(3);
        try {
            assertThat(executor.map(List.of(1, 2, 3, 4, 5, 6, 7), value -> value * value))
                    .containsExactly(1, 4, 9, 16, 25, 36, 49);
        } finally {
            executor.close();
        }
    }

    @Test
    void handlesEmptyInput() {
        BulkScreeningExecutor executor = new BulkScreeningExecutor(2);
        try {
            assertThat(executor.map(List.<Integer>of(), value -> value * 2)).isEmpty();
        } finally {
            executor.close();
        }
    }
}
