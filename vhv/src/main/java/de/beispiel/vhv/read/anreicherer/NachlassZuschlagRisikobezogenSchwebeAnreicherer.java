package de.beispiel.vhv.read.anreicherer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;

/** Schwebe: gezielte Anreicherung der Entitaet NachlassZuschlagRisikobezogen. */
@Component
public final class NachlassZuschlagRisikobezogenSchwebeAnreicherer extends BasisNachlassZuschlagRisikobezogenAnreicherer {
    public NachlassZuschlagRisikobezogenSchwebeAnreicherer(JdbcTemplate jdbc) {
        super(jdbc, Datenstand.SCHWEBE);
    }
}
