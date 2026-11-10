package de.vers.migration.risiko.read;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

/** Nachlesezugriffe auf den Altbestand. */
@Repository
public class AltBestandDao {

    /** Wert 1914 je m² Wohnfläche, hochgerechnet mit dem Baupreisindex aus FAKTOREN. */
    private static final BigDecimal WERT_1914_JE_QM = new BigDecimal("650");

    private final JdbcTemplate jdbc;

    public AltBestandDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BigDecimal ermittleSummeAusWohnflaeche(String vertragsnummer) {
        BigDecimal flaeche = jdbc.queryForObject(
                "SELECT WOHNFLAECHE FROM ALT_OBJEKT WHERE VNR = ?", BigDecimal.class, vertragsnummer);
        BigDecimal faktor = jdbc.queryForObject(
                "SELECT WERT FROM FAKTOREN WHERE NAME = 'BAUPREISINDEX'", BigDecimal.class);
        return flaeche.multiply(WERT_1914_JE_QM).multiply(faktor);
    }
}
