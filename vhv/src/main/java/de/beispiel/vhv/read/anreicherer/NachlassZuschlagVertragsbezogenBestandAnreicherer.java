package de.beispiel.vhv.read.anreicherer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;

/** Bestand: gezielte Anreicherung der Entitaet NachlassZuschlagVertragsbezogen. */
@Component
public final class NachlassZuschlagVertragsbezogenBestandAnreicherer extends BasisNachlassZuschlagVertragsbezogenAnreicherer {
    public NachlassZuschlagVertragsbezogenBestandAnreicherer(JdbcTemplate jdbc) {
        super(jdbc, Datenstand.BESTAND);
    }
}
