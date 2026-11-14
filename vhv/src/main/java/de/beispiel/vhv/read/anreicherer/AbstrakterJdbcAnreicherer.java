package de.beispiel.vhv.read.anreicherer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.read.EntitaetAnreicherer;

/** Template Method: Auswahl, Kardinalitaet, Datenstand und Zuordnung zentral. */
abstract class AbstrakterJdbcAnreicherer<T> implements EntitaetAnreicherer {
    private final JdbcTemplate jdbc;
    private final Datenstand datenstand;
    protected AbstrakterJdbcAnreicherer(JdbcTemplate jdbc, Datenstand datenstand) {
        this.jdbc = jdbc;
        this.datenstand = datenstand;
    }
    @Override public final Datenstand datenstand() { return datenstand; }
    protected abstract String sql();
    protected abstract T lese(ResultSet rs) throws SQLException;
    protected abstract void uebernehme(QuellDatensatz ziel, T wert);

    @Override
    public final void anreichern(QuellDatensatz datensatz) {
        if (datensatz.datenstand() != datenstand) {
            throw new IllegalArgumentException("Falscher Datenstand fuer " + entitaet());
        }
        List<T> zeilen = jdbc.query(sql(), (rs, nr) -> lese(rs),
                datensatz.vertragId(), datenstand.name());
        if (zeilen.size() > 1) {
            throw new IllegalStateException("Mehrdeutige " + entitaet()
                    + "-Daten zu " + datensatz.vertragId() + "/" + datenstand);
        }
        if (zeilen.isEmpty()) {
            if (entitaet() == EntitaetTyp.VERTRAG) {
                throw new IllegalStateException("Vertrag fehlt: " + datensatz.vertragId());
            }
            return; // optionale Nebendaten sind nicht in jedem Vertrag vorhanden
        }
        uebernehme(datensatz, zeilen.get(0));
    }
}
