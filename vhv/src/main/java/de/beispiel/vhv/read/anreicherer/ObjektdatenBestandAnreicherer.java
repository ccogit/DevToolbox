package de.beispiel.vhv.read.anreicherer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;

/** Bestand: gezielte Anreicherung der Entitaet Objektdaten. */
@Component
public final class ObjektdatenBestandAnreicherer extends BasisObjektdatenAnreicherer {
    public ObjektdatenBestandAnreicherer(JdbcTemplate jdbc) {
        super(jdbc, Datenstand.BESTAND);
    }
}
