package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;
import de.beispiel.vhv.modell.alt.WertsachenAlt;
import de.beispiel.vhv.modell.ziel.WertsachenZiel;

/** Feldweises Mapping: Wertsachen, inklusive normalisierender Sub-Regeln. */
@Component
public final class WertsachenSubMapper {
    private final GemeinsameSubMapper regeln;
    public WertsachenSubMapper(GemeinsameSubMapper regeln) { this.regeln = regeln; }
    public WertsachenZiel mappe(WertsachenAlt quelle, Datenstand stand) {
        if (quelle == null) return null;
        if (quelle.anzahl() != null && quelle.anzahl() < 1)
            throw new IllegalArgumentException("Wertsachen-Anzahl muss positiv sein");
        return new WertsachenZiel(
                regeln.text(quelle.kategorie()),
                regeln.nichtNegativ(quelle.einzelwert()),
                quelle.anzahl(),
                regeln.text(quelle.aufbewahrung()),
                regeln.nichtNegativ(quelle.grenze())
        );
    }
}
