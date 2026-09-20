package com.sdnmatcher.screening.service;

import com.sdnmatcher.screening.model.SdnEntry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SdnCandidateSelector {
    static final int MIN_NAME_LENGTH = 6;
    static final int MIN_TRIGRAMS = 3;
    static final int MIN_CANDIDATES = 8;
    static final int TOP_CANDIDATES = 128;
    static final double MIN_TRIGRAM_OVERLAP = 0.50;
    private static final int PREFIX_LENGTH = 4;

    private volatile CachedIndex cache;

    public List<SdnEntry> select(String normalizedName, List<SdnEntry> entries) {
        Set<String> queryTrigrams = trigrams(normalizedName);
        if (normalizedName.length() < MIN_NAME_LENGTH || queryTrigrams.size() < MIN_TRIGRAMS) {
            return entries;
        }

        CandidateIndex index = indexFor(entries);
        Workspace workspace = index.workspace();
        workspace.reset(queryTrigrams.size());
        BitSet selected = workspace.selected();

        for (String trigram : queryTrigrams) {
            int[] posting = index.trigrams().get(trigram);
            if (posting != null) {
                for (int candidate : posting) {
                    workspace.increment(candidate);
                }
            }
        }

        int minimumOverlap = Math.max(1,
                (int) Math.ceil(queryTrigrams.size() * MIN_TRIGRAM_OVERLAP));
        for (int i = 0; i < workspace.touchedSize(); i++) {
            int candidate = workspace.touchedAt(i);
            int overlap = workspace.count(candidate);
            workspace.recordOverlap(overlap);
            if (overlap >= minimumOverlap) {
                selected.set(candidate);
            }
        }
        int cutoff = workspace.topCutoff(TOP_CANDIDATES);
        int topAdded = 0;
        for (int i = 0; i < workspace.touchedSize() && topAdded < TOP_CANDIDATES; i++) {
            int candidate = workspace.touchedAt(i);
            if (workspace.count(candidate) >= cutoff) {
                selected.set(candidate);
                topAdded++;
            }
        }

        for (String token : tokens(normalizedName)) {
            addAll(selected, index.tokens().get(token));
            if (token.length() >= PREFIX_LENGTH) {
                addAll(selected, index.prefixes().get(token.substring(0, PREFIX_LENGTH)));
            }
        }

        if (selected.cardinality() < MIN_CANDIDATES) {
            return entries;
        }

        List<SdnEntry> candidates = new ArrayList<>(selected.cardinality());
        for (int i = selected.nextSetBit(0); i >= 0; i = selected.nextSetBit(i + 1)) {
            candidates.add(entries.get(i));
        }
        return List.copyOf(candidates);
    }

    private CandidateIndex indexFor(List<SdnEntry> entries) {
        CachedIndex current = cache;
        if (current != null && current.entries() == entries) {
            return current.index();
        }
        synchronized (this) {
            current = cache;
            if (current != null && current.entries() == entries) {
                return current.index();
            }
            CandidateIndex built = build(entries);
            cache = new CachedIndex(entries, built);
            return built;
        }
    }

    private static CandidateIndex build(List<SdnEntry> entries) {
        Map<String, List<Integer>> trigrams = new HashMap<>();
        Map<String, List<Integer>> tokens = new HashMap<>();
        Map<String, List<Integer>> prefixes = new HashMap<>();

        for (int i = 0; i < entries.size(); i++) {
            Set<String> entryTrigrams = new HashSet<>();
            Set<String> entryTokens = new HashSet<>();
            for (String name : entries.get(i).names()) {
                entryTrigrams.addAll(trigrams(name));
                entryTokens.addAll(tokens(name));
            }
            addToIndex(trigrams, entryTrigrams, i);
            addToIndex(tokens, entryTokens, i);
            addToIndex(prefixes, entryTokens.stream()
                    .filter(token -> token.length() >= PREFIX_LENGTH)
                    .map(token -> token.substring(0, PREFIX_LENGTH))
                    .collect(java.util.stream.Collectors.toSet()), i);
        }
        return new CandidateIndex(trigrams, tokens, prefixes, entries.size());
    }

    private static void addToIndex(Map<String, List<Integer>> index, Set<String> keys, int entryIndex) {
        for (String key : keys) {
            index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entryIndex);
        }
    }

    private static void addAll(BitSet selected, int[] candidates) {
        if (candidates != null) {
            for (int candidate : candidates) {
                selected.set(candidate);
            }
        }
    }

    static Set<String> trigrams(String value) {
        String compact = value.replace(" ", "");
        Set<String> result = new HashSet<>();
        for (int i = 0; i <= compact.length() - 3; i++) {
            result.add(compact.substring(i, i + 3));
        }
        return result;
    }

    private static Set<String> tokens(String value) {
        return new HashSet<>(List.of(value.split(" ")));
    }

    private static final class CandidateIndex {
        private final Map<String, int[]> trigrams;
        private final Map<String, int[]> tokens;
        private final Map<String, int[]> prefixes;
        private final ThreadLocal<Workspace> workspaces;

        private CandidateIndex(Map<String, List<Integer>> trigrams,
                               Map<String, List<Integer>> tokens,
                               Map<String, List<Integer>> prefixes,
                               int entryCount) {
            this.trigrams = freeze(trigrams);
            this.tokens = freeze(tokens);
            this.prefixes = freeze(prefixes);
            this.workspaces = ThreadLocal.withInitial(() -> new Workspace(entryCount));
        }

        private Map<String, int[]> trigrams() {
            return trigrams;
        }

        private Map<String, int[]> tokens() {
            return tokens;
        }

        private Map<String, int[]> prefixes() {
            return prefixes;
        }

        private Workspace workspace() {
            return workspaces.get();
        }

        private static Map<String, int[]> freeze(Map<String, List<Integer>> source) {
            Map<String, int[]> result = new HashMap<>(source.size());
            source.forEach((key, values) -> result.put(key,
                    values.stream().mapToInt(Integer::intValue).toArray()));
            return Map.copyOf(result);
        }
    }

    private static final class Workspace {
        private final int[] counts;
        private final int[] generations;
        private final int[] touched;
        private int[] overlapHistogram;
        private final BitSet selected;
        private int generation;
        private int touchedSize;

        private Workspace(int entryCount) {
            counts = new int[entryCount];
            generations = new int[entryCount];
            touched = new int[entryCount];
            overlapHistogram = new int[1];
            selected = new BitSet(entryCount);
        }

        private void reset(int maximumOverlap) {
            if (generation == Integer.MAX_VALUE) {
                Arrays.fill(generations, 0);
                generation = 0;
            }
            generation++;
            touchedSize = 0;
            selected.clear();
            if (overlapHistogram.length <= maximumOverlap) {
                overlapHistogram = new int[maximumOverlap + 1];
            } else {
                Arrays.fill(overlapHistogram, 0, maximumOverlap + 1, 0);
            }
        }

        private void increment(int candidate) {
            if (generations[candidate] != generation) {
                generations[candidate] = generation;
                counts[candidate] = 1;
                touched[touchedSize++] = candidate;
            } else {
                counts[candidate]++;
            }
        }

        private void recordOverlap(int overlap) {
            overlapHistogram[overlap]++;
        }

        private int topCutoff(int limit) {
            int accumulated = 0;
            for (int overlap = overlapHistogram.length - 1; overlap >= 1; overlap--) {
                accumulated += overlapHistogram[overlap];
                if (accumulated >= limit) {
                    return overlap;
                }
            }
            return 1;
        }

        private int touchedSize() {
            return touchedSize;
        }

        private int touchedAt(int index) {
            return touched[index];
        }

        private int count(int candidate) {
            return counts[candidate];
        }

        private BitSet selected() {
            return selected;
        }
    }

    private record CachedIndex(List<SdnEntry> entries, CandidateIndex index) {
    }
}
