package de.beispiel.vhv.write;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;

/** 13 idempotente, fachlich geordnete Schreibschritte und abschliessendes Audit. */
@Component
public final class SchreibDelegator implements ItemWriter<ZielDatensatz> {
    private final Map<EntitaetTyp, EntitaetSchreiber> schritte = new EnumMap<>(EntitaetTyp.class);
    private final JdbcTemplate jdbc;
    public SchreibDelegator(List<EntitaetSchreiber> schreiber, JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        for (EntitaetSchreiber schritt : schreiber) {
            if (schritte.putIfAbsent(schritt.entitaet(), schritt) != null)
                throw new IllegalStateException("Doppelter Schreiber: " + schritt.entitaet());
        }
        for (EntitaetTyp typ : EntitaetTyp.values()) {
            if (!schritte.containsKey(typ)) throw new IllegalStateException("Schreiber fehlt: " + typ);
        }
    }
    @Override public void write(Chunk<? extends ZielDatensatz> items) {
        for (ZielDatensatz item : items) {
            for (EntitaetTyp typ : EntitaetTyp.values())
                schritte.get(typ).schreibe(item, jdbc);
            jdbc.update("DELETE FROM MIGRATION_AUDIT WHERE VERTRAG_ID = ? AND DATENSTAND = ?",
                    item.vertragId(), item.datenstand().name());
            jdbc.update("INSERT INTO MIGRATION_AUDIT (VERTRAG_ID, DATENSTAND, ERGEBNIS) VALUES (?, ?, ?)",
                    item.vertragId(), item.datenstand().name(), "ERFOLGREICH");
        }
    }
}
