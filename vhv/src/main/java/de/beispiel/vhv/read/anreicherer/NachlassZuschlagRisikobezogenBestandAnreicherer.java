package de.beispiel.vhv.read.anreicherer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;

/** Bestand: gezielte Anreicherung der Entitaet NachlassZuschlagRisikobezogen. */
@Component
public final class NachlassZuschlagRisikobezogenBestandAnreicherer extends BasisNachlassZuschlagRisikobezogenAnreicherer {
    public NachlassZuschlagRisikobezogenBestandAnreicherer(JdbcTemplate jdbc) {
        super(jdbc, Datenstand.BESTAND);
    }
}
