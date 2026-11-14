package de.beispiel.vhv.write.schritte;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.ziel.VorversichererZiel;
import de.beispiel.vhv.write.EntitaetSchreiber;

/** Idempotentes Schreiben der Vorversicherer-Zieldaten; Teil der Chunk-Transaktion. */
@Component
public final class VorversichererSchreiber implements EntitaetSchreiber {
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.VORVERSICHERER; }
    @Override public void schreibe(ZielDatensatz item, JdbcTemplate jdbc) {
        VorversichererZiel daten = item.getVorversicherer();
        jdbc.update("DELETE FROM ZIEL_VORVERSICHERER WHERE VERTRAG_ID = ? AND DATENSTAND = ?",
                item.vertragId(), item.datenstand().name());
        if (daten == null) return; // aus der Quelle nicht belegte optionale Entitaet
        jdbc.update("INSERT INTO ZIEL_VORVERSICHERER (VERTRAG_ID, DATENSTAND, GESELLSCHAFT, VERTRAGSNUMMER_ALT, SCHADENFREI_SEIT, VORVERTRAG_ENDE, SCHADENZAHL) VALUES (?, ?, ?, ?, ?, ?, ?)",
                item.vertragId(), item.datenstand().name(),
                daten.gesellschaft(),
                daten.vertragsnummerAlt(),
                daten.schadenfreiSeit(),
                daten.vorvertragEnde(),
                daten.schadenzahl());
    }
}
