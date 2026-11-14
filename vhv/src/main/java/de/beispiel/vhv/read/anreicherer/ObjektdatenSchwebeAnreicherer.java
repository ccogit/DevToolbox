package de.beispiel.vhv.read.anreicherer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;

/** Schwebe: gezielte Anreicherung der Entitaet Objektdaten. */
@Component
public final class ObjektdatenSchwebeAnreicherer extends BasisObjektdatenAnreicherer {
    public ObjektdatenSchwebeAnreicherer(JdbcTemplate jdbc) {
        super(jdbc, Datenstand.SCHWEBE);
    }
}
