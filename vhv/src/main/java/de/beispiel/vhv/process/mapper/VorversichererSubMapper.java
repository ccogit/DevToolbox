package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;
import de.beispiel.vhv.modell.alt.VorversichererAlt;
import de.beispiel.vhv.modell.ziel.VorversichererZiel;

/** Feldweises Mapping: Vorversicherer, inklusive normalisierender Sub-Regeln. */
@Component
public final class VorversichererSubMapper {
    private final GemeinsameSubMapper regeln;
    public VorversichererSubMapper(GemeinsameSubMapper regeln) { this.regeln = regeln; }
    public VorversichererZiel mappe(VorversichererAlt quelle, Datenstand stand) {
        if (quelle == null) return null;
        return new VorversichererZiel(
                regeln.text(quelle.gesellschaft()),
                regeln.text(quelle.vertragsnummerAlt()),
                quelle.schadenfreiSeit(),
                quelle.vorvertragEnde(),
                quelle.schadenzahl()
        );
    }
}
