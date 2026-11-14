package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;
import de.beispiel.vhv.modell.alt.ObjektdatenAlt;
import de.beispiel.vhv.modell.ziel.ObjektdatenZiel;

/** Feldweises Mapping: Objektdaten, inklusive normalisierender Sub-Regeln. */
@Component
public final class ObjektdatenSubMapper {
    private final GemeinsameSubMapper regeln;
    public ObjektdatenSubMapper(GemeinsameSubMapper regeln) { this.regeln = regeln; }
    public ObjektdatenZiel mappe(ObjektdatenAlt quelle, Datenstand stand) {
        if (quelle == null) return null;
        return new ObjektdatenZiel(
                regeln.text(quelle.objektart()),
                regeln.geld(quelle.wohnflaeche()),
                quelle.baujahr(),
                regeln.code(quelle.bauartklasse()),
                regeln.text(quelle.nutzung())
        );
    }
}
