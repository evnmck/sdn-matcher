package com.sdnmatcher.screening.service;

import java.text.Normalizer;
import java.util.Locale;

public final class NameNormalizer {
    private NameNormalizer() {
    }

    public static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{Alnum}]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}
