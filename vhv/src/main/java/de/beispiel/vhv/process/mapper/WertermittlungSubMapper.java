package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;
import de.beispiel.vhv.modell.alt.WertermittlungAlt;
import de.beispiel.vhv.modell.ziel.WertermittlungZiel;

/** Feldweises Mapping: Wertermittlung, inklusive normalisierender Sub-Regeln. */
@Component
public final class WertermittlungSubMapper {
    private final GemeinsameSubMapper regeln;
    public WertermittlungSubMapper(GemeinsameSubMapper regeln) { this.regeln = regeln; }
    public WertermittlungZiel mappe(WertermittlungAlt quelle, Datenstand stand) {
        if (quelle == null) return null;
        return new WertermittlungZiel(
                regeln.text(quelle.ermittlungsart()),
                regeln.nichtNegativ(quelle.neuwert()),
                quelle.indexjahr(),
                regeln.geld(quelle.indexfaktor()),
                quelle.stand()
        );
    }
}
