package de.beispiel.vhv.read.anreicherer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;

/** Bestand: gezielte Anreicherung der Entitaet Risiko. */
@Component
public final class RisikoBestandAnreicherer extends BasisRisikoAnreicherer {
    public RisikoBestandAnreicherer(JdbcTemplate jdbc) {
        super(jdbc, Datenstand.BESTAND);
    }
}
