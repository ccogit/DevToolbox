package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.process.EntitaetMapper;

/** Hauptmapper delegiert Feld- und Unterstrukturregeln an den StandortSubMapper. */
@Component
public final class StandortMapper implements EntitaetMapper {
    private final StandortSubMapper subMapper;
    public StandortMapper(StandortSubMapper subMapper) { this.subMapper = subMapper; }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.STANDORT; }
    @Override public void mappe(QuellDatensatz quelle, ZielDatensatz ziel) {
        ziel.setStandort(subMapper.mappe(quelle.getStandort(), quelle.datenstand()));
    }
}
