package de.beispiel.vhv.read.anreicherer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;

/** Schwebe: gezielte Anreicherung der Entitaet Nebenversicherer. */
@Component
public final class NebenversichererSchwebeAnreicherer extends BasisNebenversichererAnreicherer {
    public NebenversichererSchwebeAnreicherer(JdbcTemplate jdbc) {
        super(jdbc, Datenstand.SCHWEBE);
    }
}
