package com.sdnmatcher.screening.model;

import java.time.LocalDate;
import java.util.List;

public record SdnEntry(
        String uid,
        List<String> names,
        List<LocalDate> datesOfBirth,
        List<Integer> birthYears) {
}
