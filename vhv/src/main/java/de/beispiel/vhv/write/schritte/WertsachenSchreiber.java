package de.beispiel.vhv.write.schritte;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.ziel.WertsachenZiel;
import de.beispiel.vhv.write.EntitaetSchreiber;

/** Idempotentes Schreiben der Wertsachen-Zieldaten; Teil der Chunk-Transaktion. */
@Component
public final class WertsachenSchreiber implements EntitaetSchreiber {
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.WERTSACHEN; }
    @Override public void schreibe(ZielDatensatz item, JdbcTemplate jdbc) {
        WertsachenZiel daten = item.getWertsachen();
        jdbc.update("DELETE FROM ZIEL_WERTSACHEN WHERE VERTRAG_ID = ? AND DATENSTAND = ?",
                item.vertragId(), item.datenstand().name());
        if (daten == null) return; // aus der Quelle nicht belegte optionale Entitaet
        jdbc.update("INSERT INTO ZIEL_WERTSACHEN (VERTRAG_ID, DATENSTAND, KATEGORIE, EINZELWERT, ANZAHL, AUFBEWAHRUNG, GRENZE) VALUES (?, ?, ?, ?, ?, ?, ?)",
                item.vertragId(), item.datenstand().name(),
                daten.kategorie(),
                daten.einzelwert(),
                daten.anzahl(),
                daten.aufbewahrung(),
                daten.grenze());
    }
}
