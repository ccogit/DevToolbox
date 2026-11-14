package de.beispiel.vhv.write.schritte;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.ziel.NebenversichererZiel;
import de.beispiel.vhv.write.EntitaetSchreiber;

/** Idempotentes Schreiben der Nebenversicherer-Zieldaten; Teil der Chunk-Transaktion. */
@Component
public final class NebenversichererSchreiber implements EntitaetSchreiber {
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.NEBENVERSICHERER; }
    @Override public void schreibe(ZielDatensatz item, JdbcTemplate jdbc) {
        NebenversichererZiel daten = item.getNebenversicherer();
        jdbc.update("DELETE FROM ZIEL_NEBENVERSICHERER WHERE VERTRAG_ID = ? AND DATENSTAND = ?",
                item.vertragId(), item.datenstand().name());
        if (daten == null) return; // aus der Quelle nicht belegte optionale Entitaet
        jdbc.update("INSERT INTO ZIEL_NEBENVERSICHERER (VERTRAG_ID, DATENSTAND, GESELLSCHAFT, VERTRAGSNUMMER_FREMD, ANTEIL_PROZENT, DECKUNGSBEREICH, BEGINN) VALUES (?, ?, ?, ?, ?, ?, ?)",
                item.vertragId(), item.datenstand().name(),
                daten.gesellschaft(),
                daten.vertragsnummerFremd(),
                daten.anteilProzent(),
                daten.deckungsbereich(),
                daten.beginn());
    }
}
