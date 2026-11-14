package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;
import de.beispiel.vhv.modell.alt.VertragAlt;
import de.beispiel.vhv.modell.ziel.VertragZiel;

/** Feldweises Mapping: Vertrag, inklusive normalisierender Sub-Regeln. */
@Component
public final class VertragSubMapper {
    private final GemeinsameSubMapper regeln;
    public VertragSubMapper(GemeinsameSubMapper regeln) { this.regeln = regeln; }
    public VertragZiel mappe(VertragAlt quelle, Datenstand stand) {
        if (quelle == null) return null;
        return new VertragZiel(
                regeln.text(quelle.vertragsnummer()),
                regeln.code(quelle.tarif()),
                quelle.beginn(),
                quelle.ablauf(),
                regeln.code(quelle.zahlweise())
        );
    }
}
