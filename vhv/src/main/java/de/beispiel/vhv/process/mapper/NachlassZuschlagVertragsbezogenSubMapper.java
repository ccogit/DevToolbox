package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;
import de.beispiel.vhv.modell.alt.NachlassZuschlagVertragsbezogenAlt;
import de.beispiel.vhv.modell.ziel.NachlassZuschlagVertragsbezogenZiel;

/** Feldweises Mapping: NachlassZuschlagVertragsbezogen, inklusive normalisierender Sub-Regeln. */
@Component
public final class NachlassZuschlagVertragsbezogenSubMapper {
    private final GemeinsameSubMapper regeln;
    public NachlassZuschlagVertragsbezogenSubMapper(GemeinsameSubMapper regeln) { this.regeln = regeln; }
    public NachlassZuschlagVertragsbezogenZiel mappe(NachlassZuschlagVertragsbezogenAlt quelle, Datenstand stand) {
        if (quelle == null) return null;
        return new NachlassZuschlagVertragsbezogenZiel(
                regeln.text(quelle.merkmal()),
                regeln.prozent(quelle.prozentsatz()),
                regeln.geld(quelle.betrag()),
                quelle.gueltigAb(),
                regeln.text(quelle.grund())
        );
    }
}
