package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;
import de.beispiel.vhv.modell.alt.DeckungenAlt;
import de.beispiel.vhv.modell.ziel.DeckungenZiel;

/** Feldweises Mapping: Deckungen, inklusive normalisierender Sub-Regeln. */
@Component
public final class DeckungenSubMapper {
    private final GemeinsameSubMapper regeln;
    public DeckungenSubMapper(GemeinsameSubMapper regeln) { this.regeln = regeln; }
    public DeckungenZiel mappe(DeckungenAlt quelle, Datenstand stand) {
        if (quelle == null) return null;
        return new DeckungenZiel(
                regeln.code(quelle.deckungsart()),
                regeln.nichtNegativ(quelle.summe()),
                regeln.nichtNegativ(quelle.selbstbeteiligung()),
                ("J".equalsIgnoreCase(quelle.einschluss()) ? "JA" : "N".equalsIgnoreCase(quelle.einschluss()) ? "NEIN" : regeln.code(quelle.einschluss())),
                quelle.gueltigAb()
        );
    }
}
