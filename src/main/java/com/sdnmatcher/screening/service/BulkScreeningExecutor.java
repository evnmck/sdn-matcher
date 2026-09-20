package com.sdnmatcher.screening.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

@Component
public class BulkScreeningExecutor {
    private final int parallelism;
    private final ExecutorService executor;

    public BulkScreeningExecutor() {
        this(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
    }

    BulkScreeningExecutor(int parallelism) {
        this.parallelism = Math.max(1, parallelism);
        this.executor = Executors.newFixedThreadPool(this.parallelism);
    }

    public <T, R> List<R> map(List<T> input, Function<T, R> mapper) {
        if (input.isEmpty()) {
            return List.of();
        }
        int batchSize = Math.max(1, (input.size() + parallelism - 1) / parallelism);
        List<CompletableFuture<List<R>>> batches = new ArrayList<>();
        for (int start = 0; start < input.size(); start += batchSize) {
            int from = start;
            int to = Math.min(start + batchSize, input.size());
            batches.add(CompletableFuture.supplyAsync(
                    () -> input.subList(from, to).stream().map(mapper).toList(), executor));
        }
        return batches.stream().flatMap(batch -> batch.join().stream()).toList();
    }

    @PreDestroy
    public void close() {
        executor.close();
    }
}
