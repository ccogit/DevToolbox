package de.beispiel.vhv.write.schritte;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.ziel.VorgangZiel;
import de.beispiel.vhv.write.EntitaetSchreiber;

/** Idempotentes Schreiben der Vorgang-Zieldaten; Teil der Chunk-Transaktion. */
@Component
public final class VorgangSchreiber implements EntitaetSchreiber {
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.VORGANG; }
    @Override public void schreibe(ZielDatensatz item, JdbcTemplate jdbc) {
        VorgangZiel daten = item.getVorgang();
        jdbc.update("DELETE FROM ZIEL_VORGANG WHERE VERTRAG_ID = ? AND DATENSTAND = ?",
                item.vertragId(), item.datenstand().name());
        if (daten == null) return; // aus der Quelle nicht belegte optionale Entitaet
        jdbc.update("INSERT INTO ZIEL_VORGANG (VERTRAG_ID, DATENSTAND, VORGANGSNUMMER, VORGANGSART, EINGANG, BEARBEITUNGSSTATUS, PRIORITAET) VALUES (?, ?, ?, ?, ?, ?, ?)",
                item.vertragId(), item.datenstand().name(),
                daten.vorgangsnummer(),
                daten.vorgangsart(),
                daten.eingang(),
                daten.bearbeitungsstatus(),
                daten.prioritaet());
    }
}
