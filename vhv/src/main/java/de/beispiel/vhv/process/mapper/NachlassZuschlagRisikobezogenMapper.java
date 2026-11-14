package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.process.EntitaetMapper;

/** Hauptmapper delegiert Feld- und Unterstrukturregeln an den NachlassZuschlagRisikobezogenSubMapper. */
@Component
public final class NachlassZuschlagRisikobezogenMapper implements EntitaetMapper {
    private final NachlassZuschlagRisikobezogenSubMapper subMapper;
    public NachlassZuschlagRisikobezogenMapper(NachlassZuschlagRisikobezogenSubMapper subMapper) { this.subMapper = subMapper; }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.NACHLASS_ZUSCHLAG_RISIKOBEZOGEN; }
    @Override public void mappe(QuellDatensatz quelle, ZielDatensatz ziel) {
        ziel.setNachlassZuschlagRisikobezogen(subMapper.mappe(quelle.getNachlassZuschlagRisikobezogen(), quelle.datenstand()));
    }
}
