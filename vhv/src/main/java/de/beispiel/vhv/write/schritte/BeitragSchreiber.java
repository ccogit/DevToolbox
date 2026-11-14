package de.beispiel.vhv.write.schritte;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.ziel.BeitragZiel;
import de.beispiel.vhv.write.EntitaetSchreiber;

/** Idempotentes Schreiben der Beitrag-Zieldaten; Teil der Chunk-Transaktion. */
@Component
public final class BeitragSchreiber implements EntitaetSchreiber {
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.BEITRAG; }
    @Override public void schreibe(ZielDatensatz item, JdbcTemplate jdbc) {
        BeitragZiel daten = item.getBeitrag();
        jdbc.update("DELETE FROM ZIEL_BEITRAG WHERE VERTRAG_ID = ? AND DATENSTAND = ?",
                item.vertragId(), item.datenstand().name());
        if (daten == null) return; // aus der Quelle nicht belegte optionale Entitaet
        jdbc.update("INSERT INTO ZIEL_BEITRAG (VERTRAG_ID, DATENSTAND, BRUTTOBEITRAG, NETTOBEITRAG, VERSICHERUNGSTEUER, ZAHLWEISE, FAELLIGKEIT) VALUES (?, ?, ?, ?, ?, ?, ?)",
                item.vertragId(), item.datenstand().name(),
                daten.bruttobeitrag(),
                daten.nettobeitrag(),
                daten.versicherungsteuer(),
                daten.zahlweise(),
                daten.faelligkeit());
    }
}
