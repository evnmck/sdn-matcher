package com.sdnmatcher.screening.service;

import com.sdnmatcher.screening.api.MatchResult;
import com.sdnmatcher.screening.api.ScreeningResult;
import com.sdnmatcher.screening.model.Account;
import com.sdnmatcher.screening.model.SdnEntry;
import com.sdnmatcher.screening.repository.AccountRepository;
import com.sdnmatcher.screening.repository.SdnRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScreeningService {
    private final AccountRepository accounts;
    private final SdnRepository sdn;
    private final MatchingService matching;
    private final BulkScreeningExecutor bulkExecutor;
    private final SdnCandidateSelector candidateSelector;

    public ScreeningService(AccountRepository accounts, SdnRepository sdn, MatchingService matching,
                            BulkScreeningExecutor bulkExecutor, SdnCandidateSelector candidateSelector) {
        this.accounts = accounts;
        this.sdn = sdn;
        this.matching = matching;
        this.bulkExecutor = bulkExecutor;
        this.candidateSelector = candidateSelector;
    }

    public List<ScreeningResult> screenAll() {
        List<SdnEntry> entries = sdn.findAll();
        return bulkExecutor.map(accounts.findAll(), account -> screen(account, entries));
    }

    public ScreeningResult screenOne(String accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return screen(account, sdn.findAll());
    }

    private ScreeningResult screen(Account account, List<SdnEntry> entries) {
        String normalizedName = NameNormalizer.normalize(account.fullName());
        List<MatchResult> matches = candidateSelector.select(normalizedName, entries).stream()
                .map(entry -> matching.match(normalizedName, account.dateOfBirth(), entry))
                .flatMap(java.util.Optional::stream)
                .toList();
        return new ScreeningResult(account.accountId(), matches);
    }
}
