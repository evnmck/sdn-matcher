package com.sdnmatcher.screening.model;

import java.time.LocalDate;

public record Account(
        String accountId,
        String firstName,
        String middleName,
        String lastName,
        LocalDate dateOfBirth,
        String country,
        String employer) {

    public String fullName() {
        return String.join(" ", firstName, middleName == null ? "" : middleName, lastName)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
