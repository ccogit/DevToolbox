package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;
import de.beispiel.vhv.modell.alt.NebenversichererAlt;
import de.beispiel.vhv.modell.ziel.NebenversichererZiel;

/** Feldweises Mapping: Nebenversicherer, inklusive normalisierender Sub-Regeln. */
@Component
public final class NebenversichererSubMapper {
    private final GemeinsameSubMapper regeln;
    public NebenversichererSubMapper(GemeinsameSubMapper regeln) { this.regeln = regeln; }
    public NebenversichererZiel mappe(NebenversichererAlt quelle, Datenstand stand) {
        if (quelle == null) return null;
        return new NebenversichererZiel(
                regeln.text(quelle.gesellschaft()),
                regeln.text(quelle.vertragsnummerFremd()),
                regeln.prozent(quelle.anteilProzent()),
                regeln.text(quelle.deckungsbereich()),
                quelle.beginn()
        );
    }
}
