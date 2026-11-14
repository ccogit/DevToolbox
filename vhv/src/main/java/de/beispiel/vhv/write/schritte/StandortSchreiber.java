package de.beispiel.vhv.write.schritte;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.ziel.StandortZiel;
import de.beispiel.vhv.write.EntitaetSchreiber;

/** Idempotentes Schreiben der Standort-Zieldaten; Teil der Chunk-Transaktion. */
@Component
public final class StandortSchreiber implements EntitaetSchreiber {
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.STANDORT; }
    @Override public void schreibe(ZielDatensatz item, JdbcTemplate jdbc) {
        StandortZiel daten = item.getStandort();
        jdbc.update("DELETE FROM ZIEL_STANDORT WHERE VERTRAG_ID = ? AND DATENSTAND = ?",
                item.vertragId(), item.datenstand().name());
        if (daten == null) return; // aus der Quelle nicht belegte optionale Entitaet
        jdbc.update("INSERT INTO ZIEL_STANDORT (VERTRAG_ID, DATENSTAND, STRASSE, HAUSNUMMER, PLZ, ORT, LAND) VALUES (?, ?, ?, ?, ?, ?, ?)",
                item.vertragId(), item.datenstand().name(),
                daten.strasse(),
                daten.hausnummer(),
                daten.plz(),
                daten.ort(),
                daten.land());
    }
}
