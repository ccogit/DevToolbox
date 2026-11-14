package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.process.EntitaetMapper;

/** Hauptmapper delegiert Feld- und Unterstrukturregeln an den VertragSubMapper. */
@Component
public final class VertragMapper implements EntitaetMapper {
    private final VertragSubMapper subMapper;
    public VertragMapper(VertragSubMapper subMapper) { this.subMapper = subMapper; }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.VERTRAG; }
    @Override public void mappe(QuellDatensatz quelle, ZielDatensatz ziel) {
        ziel.setVertrag(subMapper.mappe(quelle.getVertrag(), quelle.datenstand()));
    }
}
