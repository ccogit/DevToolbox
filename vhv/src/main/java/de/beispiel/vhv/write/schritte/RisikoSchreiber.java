package de.beispiel.vhv.write.schritte;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.ziel.RisikoZiel;
import de.beispiel.vhv.write.EntitaetSchreiber;

/** Idempotentes Schreiben der Risiko-Zieldaten; Teil der Chunk-Transaktion. */
@Component
public final class RisikoSchreiber implements EntitaetSchreiber {
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.RISIKO; }
    @Override public void schreibe(ZielDatensatz item, JdbcTemplate jdbc) {
        RisikoZiel daten = item.getRisiko();
        jdbc.update("DELETE FROM ZIEL_RISIKO WHERE VERTRAG_ID = ? AND DATENSTAND = ?",
                item.vertragId(), item.datenstand().name());
        if (daten == null) return; // aus der Quelle nicht belegte optionale Entitaet
        jdbc.update("INSERT INTO ZIEL_RISIKO (VERTRAG_ID, DATENSTAND, RISIKONUMMER, RISIKOART, VERSICHERUNGSSUMME, TARIFIERUNGSZONE, SELBSTBEHALT) VALUES (?, ?, ?, ?, ?, ?, ?)",
                item.vertragId(), item.datenstand().name(),
                daten.risikonummer(),
                daten.risikoart(),
                daten.versicherungssumme(),
                daten.tarifierungszone(),
                daten.selbstbehalt());
    }
}
