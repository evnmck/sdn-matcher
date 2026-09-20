package com.sdnmatcher.screening.repository;

import com.sdnmatcher.screening.config.ScreeningProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SdnRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void readsPrimaryNameAliasAndDateFromStandardOfacXml() throws Exception {
        Path xml = tempDir.resolve("sdn.xml");
        Files.writeString(xml, """
                <sdnList><sdnEntry>
                  <uid>30962</uid><firstName>Alaa Ali Ali Mohammed</firstName><lastName>AL-SAMAHI</lastName>
                  <akaList><aka><firstName>Allaa</firstName><lastName>AL-SAMAHY</lastName></aka></akaList>
                  <dateOfBirthList>
                    <dateOfBirthItem><dateOfBirth>08 Sep 1976</dateOfBirth></dateOfBirthItem>
                    <dateOfBirthItem><dateOfBirth>1981</dateOfBirth></dateOfBirthItem>
                  </dateOfBirthList>
                </sdnEntry></sdnList>
                """);

        var repository = new SdnRepository(new ScreeningProperties("", xml.toString(), 0.90));
        var entry = repository.findAll().getFirst();

        assertThat(entry.uid()).isEqualTo("30962");
        assertThat(entry.names()).contains("ALAA ALI ALI MOHAMMED AL SAMAHI", "ALLAA AL SAMAHY");
        assertThat(entry.datesOfBirth()).containsExactly(java.time.LocalDate.of(1976, 9, 8));
        assertThat(entry.birthYears()).containsExactly(1976, 1981);
    }

    @Test
    void keepsSingleApproximateYearButIgnoresYearRanges() throws Exception {
        Path xml = tempDir.resolve("sdn.xml");
        Files.writeString(xml, """
                <sdnList><sdnEntry><uid>1</uid><lastName>Example</lastName><dateOfBirthList>
                  <dateOfBirthItem><dateOfBirth>circa 1951</dateOfBirth></dateOfBirthItem>
                  <dateOfBirthItem><dateOfBirth>between 1952 and 1954</dateOfBirth></dateOfBirthItem>
                </dateOfBirthList></sdnEntry></sdnList>
                """);

        var entry = repository(xml).findAll().getFirst();

        assertThat(entry.datesOfBirth()).isEmpty();
        assertThat(entry.birthYears()).containsExactly(1951);
    }

    @Test
    void rejectsXmlContainingDoctype() throws Exception {
        Path xml = tempDir.resolve("sdn.xml");
        Files.writeString(xml, """
                <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <sdnList><sdnEntry><uid>1</uid><lastName>&xxe;</lastName></sdnEntry></sdnList>
                """);

        assertThatThrownBy(() -> repository(xml).findAll())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to parse SDN XML");
    }

    @Test
    void cachesParsedEntriesUntilTheFileChanges() throws Exception {
        Path xml = tempDir.resolve("sdn.xml");
        Files.writeString(xml, "<sdnList><sdnEntry><uid>1</uid><lastName>First</lastName></sdnEntry></sdnList>");
        SdnRepository repository = repository(xml);

        var firstRead = repository.findAll();
        var cachedRead = repository.findAll();
        Files.writeString(xml, "<sdnList><sdnEntry><uid>22</uid><lastName>Second Name</lastName></sdnEntry></sdnList>");
        var reloadedRead = repository.findAll();

        assertThat(cachedRead).isSameAs(firstRead);
        assertThat(reloadedRead).isNotSameAs(firstRead);
        assertThat(reloadedRead.getFirst().uid()).isEqualTo("22");
    }

    private SdnRepository repository(Path xml) {
        return new SdnRepository(new ScreeningProperties("", xml.toString(), 0.90));
    }
}
