package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.process.EntitaetMapper;

/** Hauptmapper delegiert Feld- und Unterstrukturregeln an den DeckungenSubMapper. */
@Component
public final class DeckungenMapper implements EntitaetMapper {
    private final DeckungenSubMapper subMapper;
    public DeckungenMapper(DeckungenSubMapper subMapper) { this.subMapper = subMapper; }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.DECKUNGEN; }
    @Override public void mappe(QuellDatensatz quelle, ZielDatensatz ziel) {
        ziel.setDeckungen(subMapper.mappe(quelle.getDeckungen(), quelle.datenstand()));
    }
}
