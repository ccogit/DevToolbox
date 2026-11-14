package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.Datenstand;
import de.beispiel.vhv.modell.alt.NachlassZuschlagRisikobezogenAlt;
import de.beispiel.vhv.modell.ziel.NachlassZuschlagRisikobezogenZiel;

/** Feldweises Mapping: NachlassZuschlagRisikobezogen, inklusive normalisierender Sub-Regeln. */
@Component
public final class NachlassZuschlagRisikobezogenSubMapper {
    private final GemeinsameSubMapper regeln;
    public NachlassZuschlagRisikobezogenSubMapper(GemeinsameSubMapper regeln) { this.regeln = regeln; }
    public NachlassZuschlagRisikobezogenZiel mappe(NachlassZuschlagRisikobezogenAlt quelle, Datenstand stand) {
        if (quelle == null) return null;
        return new NachlassZuschlagRisikobezogenZiel(
                regeln.text(quelle.merkmal()),
                regeln.prozent(quelle.prozentsatz()),
                regeln.geld(quelle.betrag()),
                quelle.gueltigAb(),
                regeln.text(quelle.grund())
        );
    }
}
