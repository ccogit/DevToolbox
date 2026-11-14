package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.process.EntitaetMapper;

/** Hauptmapper delegiert Feld- und Unterstrukturregeln an den NachlassZuschlagVertragsbezogenSubMapper. */
@Component
public final class NachlassZuschlagVertragsbezogenMapper implements EntitaetMapper {
    private final NachlassZuschlagVertragsbezogenSubMapper subMapper;
    public NachlassZuschlagVertragsbezogenMapper(NachlassZuschlagVertragsbezogenSubMapper subMapper) { this.subMapper = subMapper; }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.NACHLASS_ZUSCHLAG_VERTRAGSBEZOGEN; }
    @Override public void mappe(QuellDatensatz quelle, ZielDatensatz ziel) {
        ziel.setNachlassZuschlagVertragsbezogen(subMapper.mappe(quelle.getNachlassZuschlagVertragsbezogen(), quelle.datenstand()));
    }
}
