package com.sdnmatcher.screening.repository;

import com.sdnmatcher.screening.config.ScreeningProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void readsAllColumnsIncludingEmptyMiddleName() throws Exception {
        Path csv = writeCsv("1001,Allaa,,AL-SAMAHY,1976-09-08,Turkey,Sunshine Trading\n");
        AccountRepository repository = repository(csv);

        var account = repository.findById("1001").orElseThrow();

        assertThat(account.fullName()).isEqualTo("Allaa AL-SAMAHY");
        assertThat(account.middleName()).isEmpty();
        assertThat(account.dateOfBirth()).isEqualTo("1976-09-08");
        assertThat(account.country()).isEqualTo("Turkey");
        assertThat(account.employer()).isEqualTo("Sunshine Trading");
    }

    @Test
    void returnsEmptyForUnknownAccount() throws Exception {
        Path csv = writeCsv("1001,Allaa,,AL-SAMAHY,1976-09-08,Turkey,Sunshine Trading\n");

        assertThat(repository(csv).findById("missing")).isEmpty();
    }

    @Test
    void reportsUnreadableCsv() {
        AccountRepository repository = repository(tempDir.resolve("missing.csv"));

        assertThatThrownBy(repository::findAll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to read accounts CSV");
    }

    private Path writeCsv(String row) throws Exception {
        Path csv = tempDir.resolve("accounts.csv");
        Files.writeString(csv, "account_id,first_name,middle_name,last_name,dob,country,employer\n" + row);
        return csv;
    }

    private AccountRepository repository(Path csv) {
        return new AccountRepository(new ScreeningProperties(csv.toString(), "", 0.90));
    }
}
