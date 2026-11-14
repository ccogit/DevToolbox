package de.beispiel.vhv.read;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;

/** Delegiert jeden Anreicherungsschritt eindeutig an Bestand ODER Schwebe. */
@Component
public final class AnreicherungsDelegator {
    private final Map<EntitaetTyp, Map<Datenstand, EntitaetAnreicherer>> register =
            new EnumMap<>(EntitaetTyp.class);

    public AnreicherungsDelegator(List<EntitaetAnreicherer> anreicherer) {
        for (EntitaetAnreicherer a : anreicherer) {
            Map<Datenstand, EntitaetAnreicherer> proStand =
                register.computeIfAbsent(a.entitaet(), key -> new EnumMap<>(Datenstand.class));
            if (proStand.putIfAbsent(a.datenstand(), a) != null) {
                throw new IllegalStateException("Doppelter Anreicherer: " + a.entitaet() + "/" + a.datenstand());
            }
        }
        for (EntitaetTyp typ : EntitaetTyp.values()) {
            if (!register.containsKey(typ) || register.get(typ).size() != Datenstand.values().length) {
                throw new IllegalStateException("Bestand-/Schwebe-Anreicherer fehlt fuer " + typ);
            }
        }
    }

    public void anreichern(QuellDatensatz datensatz) {
        for (EntitaetTyp typ : EntitaetTyp.values()) {
            register.get(typ).get(datensatz.datenstand()).anreichern(datensatz);
        }
    }
}
