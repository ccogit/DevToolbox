package de.beispiel.vhv.read.anreicherer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;

/** Schwebe: gezielte Anreicherung der Entitaet NachlassZuschlagVertragsbezogen. */
@Component
public final class NachlassZuschlagVertragsbezogenSchwebeAnreicherer extends BasisNachlassZuschlagVertragsbezogenAnreicherer {
    public NachlassZuschlagVertragsbezogenSchwebeAnreicherer(JdbcTemplate jdbc) {
        super(jdbc, Datenstand.SCHWEBE);
    }
}
