package de.beispiel.vhv.process.mapper;

import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.process.EntitaetMapper;

/** Hauptmapper delegiert Feld- und Unterstrukturregeln an den VorversichererSubMapper. */
@Component
public final class VorversichererMapper implements EntitaetMapper {
    private final VorversichererSubMapper subMapper;
    public VorversichererMapper(VorversichererSubMapper subMapper) { this.subMapper = subMapper; }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.VORVERSICHERER; }
    @Override public void mappe(QuellDatensatz quelle, ZielDatensatz ziel) {
        ziel.setVorversicherer(subMapper.mappe(quelle.getVorversicherer(), quelle.datenstand()));
    }
}
