package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;
import de.beispiel.vhv.modell.alt.VorgangAlt;
import de.beispiel.vhv.modell.ziel.VorgangZiel;

/** Feldweises Mapping: Vorgang, inklusive normalisierender Sub-Regeln. */
@Component
public final class VorgangSubMapper {
    private final GemeinsameSubMapper regeln;
    public VorgangSubMapper(GemeinsameSubMapper regeln) { this.regeln = regeln; }
    public VorgangZiel mappe(VorgangAlt quelle, Datenstand stand) {
        if (quelle == null) return null;
        return new VorgangZiel(
                regeln.text(quelle.vorgangsnummer()),
                regeln.code(quelle.vorgangsart()),
                quelle.eingang(),
                (stand == Datenstand.SCHWEBE && (quelle.bearbeitungsstatus() == null || quelle.bearbeitungsstatus().isBlank()) ? "OFFEN" : regeln.code(quelle.bearbeitungsstatus())),
                quelle.prioritaet()
        );
    }
}
