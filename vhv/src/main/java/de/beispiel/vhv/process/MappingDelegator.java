package de.beispiel.vhv.process;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;

/** Entity-Mapping immer ueber registrierte Mapper, niemals per Switch im Processor. */
@Component
public final class MappingDelegator {
    private final Map<EntitaetTyp, EntitaetMapper> mapper = new EnumMap<>(EntitaetTyp.class);
    public MappingDelegator(List<EntitaetMapper> implementierungen) {
        for (EntitaetMapper m : implementierungen) {
            if (mapper.putIfAbsent(m.entitaet(), m) != null)
                throw new IllegalStateException("Doppelter Mapper: " + m.entitaet());
        }
        for (EntitaetTyp typ : EntitaetTyp.values()) {
            if (!mapper.containsKey(typ))
                throw new IllegalStateException("Mapper fehlt: " + typ);
        }
    }
    public ZielDatensatz mappe(QuellDatensatz quellDatensatz) {
        ZielDatensatz ziel = new ZielDatensatz(quellDatensatz.vertragId(), quellDatensatz.datenstand());
        for (EntitaetTyp typ : EntitaetTyp.values()) mapper.get(typ).mappe(quellDatensatz, ziel);
        if (ziel.getVertrag() == null)
            throw new IllegalArgumentException("Vertrag ohne Stammdaten: " + ziel.vertragId());
        return ziel;
    }
}
