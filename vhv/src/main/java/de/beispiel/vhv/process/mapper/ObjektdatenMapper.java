package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.process.EntitaetMapper;

/** Hauptmapper delegiert Feld- und Unterstrukturregeln an den ObjektdatenSubMapper. */
@Component
public final class ObjektdatenMapper implements EntitaetMapper {
    private final ObjektdatenSubMapper subMapper;
    public ObjektdatenMapper(ObjektdatenSubMapper subMapper) { this.subMapper = subMapper; }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.OBJEKTDATEN; }
    @Override public void mappe(QuellDatensatz quelle, ZielDatensatz ziel) {
        ziel.setObjektdaten(subMapper.mappe(quelle.getObjektdaten(), quelle.datenstand()));
    }
}
