package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;
import de.beispiel.vhv.modell.alt.StandortAlt;
import de.beispiel.vhv.modell.ziel.StandortZiel;

/** Feldweises Mapping: Standort, inklusive normalisierender Sub-Regeln. */
@Component
public final class StandortSubMapper {
    private final GemeinsameSubMapper regeln;
    public StandortSubMapper(GemeinsameSubMapper regeln) { this.regeln = regeln; }
    public StandortZiel mappe(StandortAlt quelle, Datenstand stand) {
        if (quelle == null) return null;
        return new StandortZiel(
                regeln.text(quelle.strasse()),
                regeln.text(quelle.hausnummer()),
                regeln.text(quelle.plz()),
                regeln.text(quelle.ort()),
                (quelle.land() == null || quelle.land().isBlank() ? "DE" : regeln.code(quelle.land()))
        );
    }
}
