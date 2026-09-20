package com.sdnmatcher.screening.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ScreeningResult(
        @JsonProperty("account_id") String accountId,
        List<MatchResult> matches) {
}
