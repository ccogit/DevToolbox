package de.beispiel.vhv.process;

import de.beispiel.vhv.modell.*;
public interface EntitaetMapper {
    EntitaetTyp entitaet();
    void mappe(QuellDatensatz quelle, ZielDatensatz ziel);
}
