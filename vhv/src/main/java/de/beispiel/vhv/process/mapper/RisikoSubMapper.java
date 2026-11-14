package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;
import de.beispiel.vhv.modell.alt.RisikoAlt;
import de.beispiel.vhv.modell.ziel.RisikoZiel;

/** Feldweises Mapping: Risiko, inklusive normalisierender Sub-Regeln. */
@Component
public final class RisikoSubMapper {
    private final GemeinsameSubMapper regeln;
    public RisikoSubMapper(GemeinsameSubMapper regeln) { this.regeln = regeln; }
    public RisikoZiel mappe(RisikoAlt quelle, Datenstand stand) {
        if (quelle == null) return null;
        return new RisikoZiel(
                regeln.text(quelle.risikonummer()),
                regeln.code(quelle.risikoart()),
                regeln.nichtNegativ(quelle.versicherungssumme()),
                regeln.text(quelle.tarifierungszone()),
                regeln.nichtNegativ(quelle.selbstbehalt())
        );
    }
}
