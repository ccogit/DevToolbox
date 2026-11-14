package de.beispiel.vhv;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** End-to-end: zwei Datenstaende, alle 13 Writer, optionale Datensaetze und Audit. */
@SpringBootTest(properties = "spring.batch.job.enabled=false")
class VhvMigrationIntegrationTest {
    @Autowired private JobLauncher launcher;
    @Autowired private Job vhvMigrationJob;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void migriertBestandUndSchwebeMitWiederanlauffaehigenSchreibschritten() throws Exception {
        starte("ALLE");
        assertThat(anzahl("ZIEL_VERTRAG")).isEqualTo(4);
        assertThat(anzahl("ZIEL_RISIKO")).isEqualTo(4);
        assertThat(anzahl("ZIEL_NEBENVERSICHERER")).isEqualTo(3);
        assertThat(anzahl("ZIEL_WERTSACHEN")).isEqualTo(3);
        assertThat(anzahl("MIGRATION_AUDIT")).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT VERSICHERUNGSSUMME FROM ZIEL_RISIKO "
            + "WHERE VERTRAG_ID=1001 AND DATENSTAND='SCHWEBE'", java.math.BigDecimal.class))
            .isEqualByComparingTo("90000.00");

        // Wiederholung als neue Jobinstanz: DELETE + INSERT bleibt idempotent.
        starte("ALLE");
        assertThat(anzahl("ZIEL_VERTRAG")).isEqualTo(4);
        assertThat(anzahl("MIGRATION_AUDIT")).isEqualTo(4);
    }
    private void starte(String datenstand) throws Exception {
        JobExecution e = launcher.run(vhvMigrationJob, new JobParametersBuilder()
            .addString("datenstand", datenstand)
            .addString("lauf", UUID.randomUUID().toString())
            .toJobParameters());
        assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }
    private int anzahl(String tabelle) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabelle, Integer.class);
    }
}
