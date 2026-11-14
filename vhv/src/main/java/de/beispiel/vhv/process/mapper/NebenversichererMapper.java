package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.process.EntitaetMapper;

/** Hauptmapper delegiert Feld- und Unterstrukturregeln an den NebenversichererSubMapper. */
@Component
public final class NebenversichererMapper implements EntitaetMapper {
    private final NebenversichererSubMapper subMapper;
    public NebenversichererMapper(NebenversichererSubMapper subMapper) { this.subMapper = subMapper; }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.NEBENVERSICHERER; }
    @Override public void mappe(QuellDatensatz quelle, ZielDatensatz ziel) {
        ziel.setNebenversicherer(subMapper.mappe(quelle.getNebenversicherer(), quelle.datenstand()));
    }
}
