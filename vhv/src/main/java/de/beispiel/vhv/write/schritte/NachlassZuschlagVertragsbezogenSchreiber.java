package de.beispiel.vhv.write.schritte;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.ziel.NachlassZuschlagVertragsbezogenZiel;
import de.beispiel.vhv.write.EntitaetSchreiber;

/** Idempotentes Schreiben der NachlassZuschlagVertragsbezogen-Zieldaten; Teil der Chunk-Transaktion. */
@Component
public final class NachlassZuschlagVertragsbezogenSchreiber implements EntitaetSchreiber {
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.NACHLASS_ZUSCHLAG_VERTRAGSBEZOGEN; }
    @Override public void schreibe(ZielDatensatz item, JdbcTemplate jdbc) {
        NachlassZuschlagVertragsbezogenZiel daten = item.getNachlassZuschlagVertragsbezogen();
        jdbc.update("DELETE FROM ZIEL_NACHLASSZUSCHLAGVERTRAGSBEZOGEN WHERE VERTRAG_ID = ? AND DATENSTAND = ?",
                item.vertragId(), item.datenstand().name());
        if (daten == null) return; // aus der Quelle nicht belegte optionale Entitaet
        jdbc.update("INSERT INTO ZIEL_NACHLASSZUSCHLAGVERTRAGSBEZOGEN (VERTRAG_ID, DATENSTAND, MERKMAL, PROZENTSATZ, BETRAG, GUELTIG_AB, GRUND) VALUES (?, ?, ?, ?, ?, ?, ?)",
                item.vertragId(), item.datenstand().name(),
                daten.merkmal(),
                daten.prozentsatz(),
                daten.betrag(),
                daten.gueltigAb(),
                daten.grund());
    }
}
