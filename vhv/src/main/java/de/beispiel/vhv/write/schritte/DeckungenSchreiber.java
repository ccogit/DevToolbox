package de.beispiel.vhv.write.schritte;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.ziel.DeckungenZiel;
import de.beispiel.vhv.write.EntitaetSchreiber;

/** Idempotentes Schreiben der Deckungen-Zieldaten; Teil der Chunk-Transaktion. */
@Component
public final class DeckungenSchreiber implements EntitaetSchreiber {
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.DECKUNGEN; }
    @Override public void schreibe(ZielDatensatz item, JdbcTemplate jdbc) {
        DeckungenZiel daten = item.getDeckungen();
        jdbc.update("DELETE FROM ZIEL_DECKUNGEN WHERE VERTRAG_ID = ? AND DATENSTAND = ?",
                item.vertragId(), item.datenstand().name());
        if (daten == null) return; // aus der Quelle nicht belegte optionale Entitaet
        jdbc.update("INSERT INTO ZIEL_DECKUNGEN (VERTRAG_ID, DATENSTAND, DECKUNGSART, SUMME, SELBSTBETEILIGUNG, EINSCHLUSS, GUELTIG_AB) VALUES (?, ?, ?, ?, ?, ?, ?)",
                item.vertragId(), item.datenstand().name(),
                daten.deckungsart(),
                daten.summe(),
                daten.selbstbeteiligung(),
                daten.einschluss(),
                daten.gueltigAb());
    }
}
