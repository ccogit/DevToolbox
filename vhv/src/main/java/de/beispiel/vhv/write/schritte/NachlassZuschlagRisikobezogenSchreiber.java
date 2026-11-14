package de.beispiel.vhv.write.schritte;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.ziel.NachlassZuschlagRisikobezogenZiel;
import de.beispiel.vhv.write.EntitaetSchreiber;

/** Idempotentes Schreiben der NachlassZuschlagRisikobezogen-Zieldaten; Teil der Chunk-Transaktion. */
@Component
public final class NachlassZuschlagRisikobezogenSchreiber implements EntitaetSchreiber {
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.NACHLASS_ZUSCHLAG_RISIKOBEZOGEN; }
    @Override public void schreibe(ZielDatensatz item, JdbcTemplate jdbc) {
        NachlassZuschlagRisikobezogenZiel daten = item.getNachlassZuschlagRisikobezogen();
        jdbc.update("DELETE FROM ZIEL_NACHLASSZUSCHLAGRISIKOBEZOGEN WHERE VERTRAG_ID = ? AND DATENSTAND = ?",
                item.vertragId(), item.datenstand().name());
        if (daten == null) return; // aus der Quelle nicht belegte optionale Entitaet
        jdbc.update("INSERT INTO ZIEL_NACHLASSZUSCHLAGRISIKOBEZOGEN (VERTRAG_ID, DATENSTAND, MERKMAL, PROZENTSATZ, BETRAG, GUELTIG_AB, GRUND) VALUES (?, ?, ?, ?, ?, ?, ?)",
                item.vertragId(), item.datenstand().name(),
                daten.merkmal(),
                daten.prozentsatz(),
                daten.betrag(),
                daten.gueltigAb(),
                daten.grund());
    }
}
