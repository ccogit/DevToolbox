package de.vers.migration.risiko.mapping;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Liest die Umschlüsselungstabelle Risikoart alt -> neu.
 * Heißt "...Dao", liegt aber im Verzeichnis "mapping" und gehört deshalb zur Process-Phase.
 */
@Repository
public class RisikoSchluesselDao {

    private final JdbcTemplate jdbc;

    public RisikoSchluesselDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String neuerSchluessel(String altcode) {
        return jdbc.queryForObject("SELECT NEU FROM MAP_RISIKOART WHERE ALT = ?", String.class, altcode);
    }
}
