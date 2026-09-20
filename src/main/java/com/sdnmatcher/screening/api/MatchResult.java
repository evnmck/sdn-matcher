package com.sdnmatcher.screening.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MatchResult(
        String uid,
        Confidence confidence,
        @JsonProperty("match_types") List<MatchType> matchTypes) {
}
