package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;
import de.beispiel.vhv.modell.alt.BeitragAlt;
import de.beispiel.vhv.modell.ziel.BeitragZiel;

/** Feldweises Mapping: Beitrag, inklusive normalisierender Sub-Regeln. */
@Component
public final class BeitragSubMapper {
    private final GemeinsameSubMapper regeln;
    public BeitragSubMapper(GemeinsameSubMapper regeln) { this.regeln = regeln; }
    public BeitragZiel mappe(BeitragAlt quelle, Datenstand stand) {
        if (quelle == null) return null;
        if (quelle.bruttobeitrag() != null && quelle.nettobeitrag() != null
                && quelle.bruttobeitrag().compareTo(quelle.nettobeitrag()) < 0)
            throw new IllegalArgumentException("Bruttobeitrag kleiner als Nettobeitrag");
        return new BeitragZiel(
                regeln.geld(quelle.bruttobeitrag()),
                regeln.geld(quelle.nettobeitrag()),
                regeln.geld(quelle.versicherungsteuer()),
                regeln.code(quelle.zahlweise()),
                quelle.faelligkeit()
        );
    }
}
