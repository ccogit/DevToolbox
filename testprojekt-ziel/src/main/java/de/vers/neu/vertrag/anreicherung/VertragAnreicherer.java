package de.vers.neu.vertrag.anreicherung;

import de.vers.neu.common.Anreicherer;
import de.vers.neu.vertrag.to.VertragTO;
import org.springframework.jdbc.core.JdbcTemplate;

/** Ergänzt die Zahlweise aus ALT_ZAHLWEG, falls ALT_VERTRAG.ZW leer ist. */
public class VertragAnreicherer implements Anreicherer<VertragTO> {

    private static final String SQL = "SELECT ZW FROM ALT_ZAHLWEG WHERE VNR = ?";

    private final JdbcTemplate altDb;

    public VertragAnreicherer(JdbcTemplate altDb) {
        this.altDb = altDb;
    }

    @Override
    public void reichereAn(VertragTO to) {
        if (to.getZahlweise() == null || to.getZahlweise().isBlank()) {
            to.setZahlweise(altDb.queryForObject(SQL, String.class, to.getVertragsnummer()));
        }
    }
}
