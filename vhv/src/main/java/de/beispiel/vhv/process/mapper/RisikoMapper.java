package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.process.EntitaetMapper;

/** Hauptmapper delegiert Feld- und Unterstrukturregeln an den RisikoSubMapper. */
@Component
public final class RisikoMapper implements EntitaetMapper {
    private final RisikoSubMapper subMapper;
    public RisikoMapper(RisikoSubMapper subMapper) { this.subMapper = subMapper; }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.RISIKO; }
    @Override public void mappe(QuellDatensatz quelle, ZielDatensatz ziel) {
        ziel.setRisiko(subMapper.mappe(quelle.getRisiko(), quelle.datenstand()));
    }
}
