package com.sdnmatcher.screening.service;

import com.sdnmatcher.screening.model.SdnEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SdnCandidateSelectorTest {
    private final SdnCandidateSelector selector = new SdnCandidateSelector();

    @Test
    void usesExhaustiveSearchForShortNames() {
        List<SdnEntry> entries = entries(100);

        assertThat(selector.select("LI", entries)).isSameAs(entries);
    }

    @Test
    void usesExhaustiveSearchWhenCandidateSetIsSparse() {
        List<SdnEntry> entries = entries(100);

        assertThat(selector.select("XYLOPHONE QUARTZ", entries)).isSameAs(entries);
    }

    @Test
    void combinesTrigramTokenAndPrefixCandidatesWhilePruningTheFullList() {
        List<SdnEntry> entries = new ArrayList<>();
        entries.add(entry("trigram", "JONATHAN SMITH"));
        entries.add(entry("token", "ROBERT SMITH"));
        entries.add(entry("prefix", "JOHNNY SMYTHE"));
        for (int i = 0; i < 300; i++) {
            entries.add(entry("overlap-" + i, "JOHN SMITHSON " + i));
        }
        entries.add(entry("unrelated", "XYLOPHONE QUARTZ"));
        List<SdnEntry> immutableEntries = List.copyOf(entries);

        List<SdnEntry> selected = selector.select("JOHN SMITH", immutableEntries);

        assertThat(selected).extracting(SdnEntry::uid)
                .contains("trigram", "token", "prefix")
                .doesNotContain("unrelated");
        assertThat(selected.size()).isLessThan(immutableEntries.size());
    }

    @Test
    void cachesIndexForTheSameImmutableSnapshot() {
        List<SdnEntry> entries = entries(100);

        List<SdnEntry> first = selector.select("PERSON NUMBER", entries);
        List<SdnEntry> second = selector.select("PERSON NUMBER", entries);

        assertThat(second).containsExactlyElementsOf(first);
    }

    @Test
    void createsExpectedTrigramsAcrossWhitespace() {
        assertThat(SdnCandidateSelector.trigrams("JO SMITH"))
                .contains("JOS", "OSM", "SMI", "MIT", "ITH");
    }

    @Test
    void acceptsSdnNamesContainingRepeatedTokens() {
        List<SdnEntry> entries = new ArrayList<>(entries(100));
        entries.add(entry("repeated", "DE DE GROUP"));

        assertThat(selector.select("GROUP DE", List.copyOf(entries)))
                .extracting(SdnEntry::uid)
                .contains("repeated");
    }

    private static List<SdnEntry> entries(int count) {
        List<SdnEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(entry(Integer.toString(i), "PERSON " + i));
        }
        return List.copyOf(entries);
    }

    private static SdnEntry entry(String uid, String name) {
        return new SdnEntry(uid, List.of(name), List.of(), List.of());
    }
}
