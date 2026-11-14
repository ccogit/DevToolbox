package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.process.EntitaetMapper;

/** Hauptmapper delegiert Feld- und Unterstrukturregeln an den WertermittlungSubMapper. */
@Component
public final class WertermittlungMapper implements EntitaetMapper {
    private final WertermittlungSubMapper subMapper;
    public WertermittlungMapper(WertermittlungSubMapper subMapper) { this.subMapper = subMapper; }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.WERTERMITTLUNG; }
    @Override public void mappe(QuellDatensatz quelle, ZielDatensatz ziel) {
        ziel.setWertermittlung(subMapper.mappe(quelle.getWertermittlung(), quelle.datenstand()));
    }
}
