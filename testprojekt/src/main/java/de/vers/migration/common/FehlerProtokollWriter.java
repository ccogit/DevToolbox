package de.vers.migration.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Schreibt fachliche Fehler in MIG_FEHLER. Ohne Suchbegriff - Referenz der Write-Phase. */
@Component
public class FehlerProtokollWriter {

    private final JdbcTemplate jdbc;

    public FehlerProtokollWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void protokolliere(String vertragsnummer, String meldung) {
        jdbc.update("INSERT INTO MIG_FEHLER (MANDANT, VNR, MELDUNG) VALUES (?, ?, ?)",
                Konstanten.MIGRATIONS_MANDANT, vertragsnummer, meldung);
    }
}
