package com.sdnmatcher.screening.service;

import com.sdnmatcher.screening.api.Confidence;
import com.sdnmatcher.screening.api.MatchResult;
import com.sdnmatcher.screening.api.MatchType;
import com.sdnmatcher.screening.model.Account;
import com.sdnmatcher.screening.model.SdnEntry;
import com.sdnmatcher.screening.repository.AccountRepository;
import com.sdnmatcher.screening.repository.SdnRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScreeningServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final SdnRepository sdn = mock(SdnRepository.class);
    private final MatchingService matching = mock(MatchingService.class);
    private final BulkScreeningExecutor bulkExecutor = new BulkScreeningExecutor(2);
    private final ScreeningService service = new ScreeningService(accounts, sdn, matching, bulkExecutor);

    @AfterEach
    void closeExecutor() {
        bulkExecutor.close();
    }

    @Test
    void screensOneAccountAndReturnsOnlyMatches() {
        Account account = account("1001");
        SdnEntry matchingEntry = entry("30962");
        SdnEntry nonMatchingEntry = entry("2");
        MatchResult match = new MatchResult("30962", Confidence.HIGH,
                List.of(MatchType.NAME_EXACT, MatchType.DOB_FULL));
        when(accounts.findById("1001")).thenReturn(Optional.of(account));
        when(sdn.findAll()).thenReturn(List.of(matchingEntry, nonMatchingEntry));
        when(matching.match("TEST PERSON", account.dateOfBirth(), matchingEntry)).thenReturn(Optional.of(match));
        when(matching.match("TEST PERSON", account.dateOfBirth(), nonMatchingEntry)).thenReturn(Optional.empty());

        var result = service.screenOne("1001");

        assertThat(result.accountId()).isEqualTo("1001");
        assertThat(result.matches()).containsExactly(match);
    }

    @Test
    void missingAccountThrowsBeforeLoadingSdnData() {
        when(accounts.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.screenOne("missing"))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found: missing");
        verify(sdn, never()).findAll();
    }

    @Test
    void bulkScreenLoadsSdnOnceAndReturnsEveryAccount() {
        Account first = account("1");
        Account second = account("2");
        SdnEntry entry = entry("9");
        when(accounts.findAll()).thenReturn(List.of(first, second));
        when(sdn.findAll()).thenReturn(List.of(entry));
        when(matching.match("TEST PERSON", first.dateOfBirth(), entry)).thenReturn(Optional.empty());
        when(matching.match("TEST PERSON", second.dateOfBirth(), entry)).thenReturn(Optional.empty());

        var results = service.screenAll();

        assertThat(results).extracting(result -> result.accountId()).containsExactly("1", "2");
        verify(sdn).findAll();
    }

    private Account account(String id) {
        return new Account(id, "Test", "", "Person", LocalDate.of(1980, 1, 1), "US", "Employer");
    }

    private SdnEntry entry(String uid) {
        return new SdnEntry(uid, List.of("TEST PERSON"), List.of(), List.of());
    }
}
