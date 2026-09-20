package com.sdnmatcher.screening.service;

import com.sdnmatcher.screening.api.Confidence;
import com.sdnmatcher.screening.api.MatchResult;
import com.sdnmatcher.screening.api.MatchType;
import com.sdnmatcher.screening.config.ScreeningProperties;
import com.sdnmatcher.screening.model.Account;
import com.sdnmatcher.screening.model.SdnEntry;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class MatchingService {
    private final JaroWinklerSimilarity similarity = new JaroWinklerSimilarity();
    private final double threshold;

    public MatchingService(ScreeningProperties properties) {
        this.threshold = properties.nameThreshold();
    }

    public Optional<MatchResult> match(Account account, SdnEntry entry) {
        return match(NameNormalizer.normalize(account.fullName()), account.dateOfBirth(), entry);
    }

    public Optional<MatchResult> match(String normalizedAccountName, LocalDate accountDob, SdnEntry entry) {
        NameMatch bestName = entry.names().stream()
                .map(name -> compareNames(normalizedAccountName, name))
                .max(Comparator.comparingDouble(NameMatch::score))
                .orElse(new NameMatch(false, 0));
        if (bestName.score() < threshold) {
            return Optional.empty();
        }

        boolean fullDob = entry.datesOfBirth().contains(accountDob);
        boolean yearDob = entry.birthYears().contains(accountDob.getYear());

        List<MatchType> types = new ArrayList<>();
        types.add(bestName.exact() ? MatchType.NAME_EXACT : MatchType.NAME_FUZZY);
        if (fullDob) {
            types.add(MatchType.DOB_FULL);
        } else if (yearDob) {
            types.add(MatchType.DOB_YEAR);
        }

        Confidence confidence = confidence(bestName.exact(), fullDob, yearDob);
        return Optional.of(new MatchResult(entry.uid(), confidence, List.copyOf(types)));
    }

    private Confidence confidence(boolean exactName, boolean fullDob, boolean yearDob) {
        if (fullDob || (exactName && yearDob)) {
            return Confidence.HIGH;
        }
        if (yearDob) {
            return Confidence.MED;
        }
        return Confidence.LOW;
    }

    private NameMatch compareNames(String left, String right) {
        return new NameMatch(left.equals(right), similarity.apply(left, right));
    }

    private record NameMatch(boolean exact, double score) {
    }
}
