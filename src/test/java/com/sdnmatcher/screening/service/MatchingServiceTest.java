package com.sdnmatcher.screening.service;

import com.sdnmatcher.screening.api.Confidence;
import com.sdnmatcher.screening.api.MatchType;
import com.sdnmatcher.screening.config.ScreeningProperties;
import com.sdnmatcher.screening.model.Account;
import com.sdnmatcher.screening.model.SdnEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingServiceTest {
    private final MatchingService service = new MatchingService(new ScreeningProperties("", "", 0.90));

    @Test
    void exactNameAndMatchingYearIsHighConfidence() {
        var result = service.match(account("Robert Mugabe", "1924-07-15"),
                entry("Robert Mugabe", "1924-02-21")).orElseThrow();

        assertThat(result.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(result.matchTypes()).containsExactly(MatchType.NAME_EXACT, MatchType.DOB_YEAR);
    }

    @Test
    void fuzzyNameAndFullDobIsHighConfidence() {
        var result = service.match(account("Allaa Al-Samahy", "1976-09-08"),
                entry("Alaa Al Samahy", "1976-09-08")).orElseThrow();

        assertThat(result.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(result.matchTypes()).containsExactly(MatchType.NAME_FUZZY, MatchType.DOB_FULL);
    }

    @Test
    void fuzzyNameWithoutDobIsLowConfidence() {
        var result = service.match(account("Roboert Mugabe", "1990-12-12"),
                entry("Robert Mugabe", "1924-02-21")).orElseThrow();

        assertThat(result.confidence()).isEqualTo(Confidence.LOW);
        assertThat(result.matchTypes()).containsExactly(MatchType.NAME_FUZZY);
    }

    @Test
    void fuzzyNameAndMatchingYearIsMediumConfidence() {
        var result = service.match(account("Alaa Al Samahy", "1976-01-01"),
                entry("Allaa Al-Samahy", "1976-09-08")).orElseThrow();

        assertThat(result.confidence()).isEqualTo(Confidence.MED);
        assertThat(result.matchTypes()).containsExactly(MatchType.NAME_FUZZY, MatchType.DOB_YEAR);
    }

    @Test
    void similarButBelowThresholdNameDoesNotMatchEvenWhenYearMatches() {
        assertThat(service.match(account("Ala Al Samahy", "1976-01-01"),
                entry("Allaa Al-Samahy", "1976-09-08"))).isEmpty();
    }

    @Test
    void exactNameWithoutMatchingDobIsLowConfidence() {
        var result = service.match(account("Robert Mugabe", "1990-12-12"),
                entry("Robert Mugabe", "1924-02-21")).orElseThrow();

        assertThat(result.confidence()).isEqualTo(Confidence.LOW);
        assertThat(result.matchTypes()).containsExactly(MatchType.NAME_EXACT);
    }

    @Test
    void punctuationCaseWhitespaceAndDiacriticsAreNormalized() {
        var result = service.match(account("Jose O-Neill", "1980-01-01"),
                entry("  JOSÉ   O'NEILL ", "1970-01-01")).orElseThrow();

        assertThat(result.matchTypes()).containsExactly(MatchType.NAME_EXACT);
    }

    @Test
    void bestMatchingAliasIsUsed() {
        Account account = account("Allaa Al-Samahy", "1976-09-08");
        LocalDate date = LocalDate.parse("1976-09-08");
        SdnEntry entry = new SdnEntry("30962", normalized("Alaa Ali Ali Mohammed AL-SAMAHI", "Allaa AL-SAMAHY"),
                List.of(date), List.of(1976));

        var result = service.match(account, entry).orElseThrow();

        assertThat(result.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(result.matchTypes()).containsExactly(MatchType.NAME_EXACT, MatchType.DOB_FULL);
    }

    @Test
    void configuredThresholdIsEnforced() {
        MatchingService exactOnly = new MatchingService(new ScreeningProperties("", "", 1.0));

        assertThat(exactOnly.match(account("Roboert Mugabe", "1924-02-21"),
                entry("Robert Mugabe", "1924-02-21"))).isEmpty();
    }

    @Test
    void unrelatedNameDoesNotMatch() {
        assertThat(service.match(account("James Lee", "1997-08-16"),
                entry("Robert Mugabe", "1924-02-21"))).isEmpty();
    }

    private Account account(String name, String dob) {
        String[] parts = name.split(" ", 2);
        return new Account("1", parts[0], "", parts[1], LocalDate.parse(dob), "", "");
    }

    private SdnEntry entry(String name, String dob) {
        LocalDate date = LocalDate.parse(dob);
        return new SdnEntry("999", normalized(name), List.of(date), List.of(date.getYear()));
    }

    private List<String> normalized(String... names) {
        return java.util.Arrays.stream(names).map(NameNormalizer::normalize).toList();
    }
}
