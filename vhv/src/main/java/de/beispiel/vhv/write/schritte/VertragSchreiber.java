package de.beispiel.vhv.write.schritte;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.ziel.VertragZiel;
import de.beispiel.vhv.write.EntitaetSchreiber;

/** Idempotentes Schreiben der Vertrag-Zieldaten; Teil der Chunk-Transaktion. */
@Component
public final class VertragSchreiber implements EntitaetSchreiber {
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.VERTRAG; }
    @Override public void schreibe(ZielDatensatz item, JdbcTemplate jdbc) {
        VertragZiel daten = item.getVertrag();
        jdbc.update("DELETE FROM ZIEL_VERTRAG WHERE VERTRAG_ID = ? AND DATENSTAND = ?",
                item.vertragId(), item.datenstand().name());
        if (daten == null) return; // aus der Quelle nicht belegte optionale Entitaet
        jdbc.update("INSERT INTO ZIEL_VERTRAG (VERTRAG_ID, DATENSTAND, VERTRAGSNUMMER, TARIF, BEGINN, ABLAUF, ZAHLWEISE) VALUES (?, ?, ?, ?, ?, ?, ?)",
                item.vertragId(), item.datenstand().name(),
                daten.vertragsnummer(),
                daten.tarif(),
                daten.beginn(),
                daten.ablauf(),
                daten.zahlweise());
    }
}
