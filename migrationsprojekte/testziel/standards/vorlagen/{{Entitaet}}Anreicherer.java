package de.vers.neu.{{entitaet}}.anreicherung;

import de.vers.neu.common.Anreicherer;
import de.vers.neu.{{entitaet}}.to.{{Entitaet}}TO;
import org.springframework.jdbc.core.JdbcTemplate;

/** Ergänzt die Zahlweise aus ALT_ZAHLWEG, falls ALT_{{ENTITAET}}.ZW leer ist. */
public class {{Entitaet}}Anreicherer implements Anreicherer<{{Entitaet}}TO> {

    private static final String SQL = "SELECT ZW FROM ALT_ZAHLWEG WHERE VNR = ?";

    private final JdbcTemplate altDb;

    public {{Entitaet}}Anreicherer(JdbcTemplate altDb) {
        this.altDb = altDb;
    }

    @Override
    public void reichereAn({{Entitaet}}TO to) {
        if (to.getZahlweise() == null || to.getZahlweise().isBlank()) {
            to.setZahlweise(altDb.queryForObject(SQL, String.class, to.getVertragsnummer()));
        }
    }
}
