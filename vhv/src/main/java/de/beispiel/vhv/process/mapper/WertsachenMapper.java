package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.process.EntitaetMapper;

/** Hauptmapper delegiert Feld- und Unterstrukturregeln an den WertsachenSubMapper. */
@Component
public final class WertsachenMapper implements EntitaetMapper {
    private final WertsachenSubMapper subMapper;
    public WertsachenMapper(WertsachenSubMapper subMapper) { this.subMapper = subMapper; }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.WERTSACHEN; }
    @Override public void mappe(QuellDatensatz quelle, ZielDatensatz ziel) {
        ziel.setWertsachen(subMapper.mappe(quelle.getWertsachen(), quelle.datenstand()));
    }
}
