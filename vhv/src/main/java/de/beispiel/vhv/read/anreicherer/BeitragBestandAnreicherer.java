package de.beispiel.vhv.read.anreicherer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;

/** Bestand: gezielte Anreicherung der Entitaet Beitrag. */
@Component
public final class BeitragBestandAnreicherer extends BasisBeitragAnreicherer {
    public BeitragBestandAnreicherer(JdbcTemplate jdbc) {
        super(jdbc, Datenstand.BESTAND);
    }
}
