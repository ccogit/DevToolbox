package de.beispiel.vhv.read.anreicherer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;

/** Schwebe: gezielte Anreicherung der Entitaet Beitrag. */
@Component
public final class BeitragSchwebeAnreicherer extends BasisBeitragAnreicherer {
    public BeitragSchwebeAnreicherer(JdbcTemplate jdbc) {
        super(jdbc, Datenstand.SCHWEBE);
    }
}
