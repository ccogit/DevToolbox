package de.beispiel.vhv.write.schritte;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.ziel.ObjektdatenZiel;
import de.beispiel.vhv.write.EntitaetSchreiber;

/** Idempotentes Schreiben der Objektdaten-Zieldaten; Teil der Chunk-Transaktion. */
@Component
public final class ObjektdatenSchreiber implements EntitaetSchreiber {
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.OBJEKTDATEN; }
    @Override public void schreibe(ZielDatensatz item, JdbcTemplate jdbc) {
        ObjektdatenZiel daten = item.getObjektdaten();
        jdbc.update("DELETE FROM ZIEL_OBJEKTDATEN WHERE VERTRAG_ID = ? AND DATENSTAND = ?",
                item.vertragId(), item.datenstand().name());
        if (daten == null) return; // aus der Quelle nicht belegte optionale Entitaet
        jdbc.update("INSERT INTO ZIEL_OBJEKTDATEN (VERTRAG_ID, DATENSTAND, OBJEKTART, WOHNFLAECHE, BAUJAHR, BAUARTKLASSE, NUTZUNG) VALUES (?, ?, ?, ?, ?, ?, ?)",
                item.vertragId(), item.datenstand().name(),
                daten.objektart(),
                daten.wohnflaeche(),
                daten.baujahr(),
                daten.bauartklasse(),
                daten.nutzung());
    }
}
