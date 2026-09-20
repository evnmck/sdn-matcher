package com.sdnmatcher.screening.repository;

import com.sdnmatcher.screening.config.ScreeningProperties;
import com.sdnmatcher.screening.model.Account;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class AccountRepository {
    private final ScreeningProperties properties;

    public AccountRepository(ScreeningProperties properties) {
        this.properties = properties;
    }

    public List<Account> findAll() {
        try (Reader reader = Files.newBufferedReader(Path.of(properties.accountsPath()))) {
            return CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .get()
                    .parse(reader)
                    .stream()
                    .map(this::toAccount)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read accounts CSV", exception);
        }
    }

    public Optional<Account> findById(String accountId) {
        return findAll().stream().filter(account -> account.accountId().equals(accountId)).findFirst();
    }

    private Account toAccount(CSVRecord row) {
        return new Account(row.get("account_id"), row.get("first_name"), row.get("middle_name"),
                row.get("last_name"), LocalDate.parse(row.get("dob")), row.get("country"), row.get("employer"));
    }
}
