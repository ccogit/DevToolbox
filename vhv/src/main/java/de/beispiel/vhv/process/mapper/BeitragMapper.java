package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.process.EntitaetMapper;

/** Hauptmapper delegiert Feld- und Unterstrukturregeln an den BeitragSubMapper. */
@Component
public final class BeitragMapper implements EntitaetMapper {
    private final BeitragSubMapper subMapper;
    public BeitragMapper(BeitragSubMapper subMapper) { this.subMapper = subMapper; }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.BEITRAG; }
    @Override public void mappe(QuellDatensatz quelle, ZielDatensatz ziel) {
        ziel.setBeitrag(subMapper.mappe(quelle.getBeitrag(), quelle.datenstand()));
    }
}
