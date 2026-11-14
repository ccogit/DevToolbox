package de.beispiel.vhv.write.schritte;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.ziel.WertermittlungZiel;
import de.beispiel.vhv.write.EntitaetSchreiber;

/** Idempotentes Schreiben der Wertermittlung-Zieldaten; Teil der Chunk-Transaktion. */
@Component
public final class WertermittlungSchreiber implements EntitaetSchreiber {
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.WERTERMITTLUNG; }
    @Override public void schreibe(ZielDatensatz item, JdbcTemplate jdbc) {
        WertermittlungZiel daten = item.getWertermittlung();
        jdbc.update("DELETE FROM ZIEL_WERTERMITTLUNG WHERE VERTRAG_ID = ? AND DATENSTAND = ?",
                item.vertragId(), item.datenstand().name());
        if (daten == null) return; // aus der Quelle nicht belegte optionale Entitaet
        jdbc.update("INSERT INTO ZIEL_WERTERMITTLUNG (VERTRAG_ID, DATENSTAND, ERMITTLUNGSART, NEUWERT, INDEXJAHR, INDEXFAKTOR, STAND) VALUES (?, ?, ?, ?, ?, ?, ?)",
                item.vertragId(), item.datenstand().name(),
                daten.ermittlungsart(),
                daten.neuwert(),
                daten.indexjahr(),
                daten.indexfaktor(),
                daten.stand());
    }
}
