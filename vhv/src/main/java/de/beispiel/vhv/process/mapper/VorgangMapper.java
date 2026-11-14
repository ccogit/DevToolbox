package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.process.EntitaetMapper;

/** Hauptmapper delegiert Feld- und Unterstrukturregeln an den VorgangSubMapper. */
@Component
public final class VorgangMapper implements EntitaetMapper {
    private final VorgangSubMapper subMapper;
    public VorgangMapper(VorgangSubMapper subMapper) { this.subMapper = subMapper; }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.VORGANG; }
    @Override public void mappe(QuellDatensatz quelle, ZielDatensatz ziel) {
        ziel.setVorgang(subMapper.mappe(quelle.getVorgang(), quelle.datenstand()));
    }
}
