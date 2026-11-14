package de.beispiel.vhv.read.anreicherer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;

/** Bestand: gezielte Anreicherung der Entitaet Vorgang. */
@Component
public final class VorgangBestandAnreicherer extends BasisVorgangAnreicherer {
    public VorgangBestandAnreicherer(JdbcTemplate jdbc) {
        super(jdbc, Datenstand.BESTAND);
    }
}
