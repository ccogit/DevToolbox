package de.beispiel.vhv.read.anreicherer;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.alt.StandortAlt;

/** Entitaetsabfrage mit expliziten Spalten; beide Datenstaende nutzen dieselbe Quellstruktur. */
abstract class BasisStandortAnreicherer extends AbstrakterJdbcAnreicherer<StandortAlt> {
    protected BasisStandortAnreicherer(JdbcTemplate jdbc, Datenstand datenstand) {
        super(jdbc, datenstand);
    }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.STANDORT; }
    @Override protected String sql() {
        return "SELECT STRASSE, HAUSNUMMER, PLZ, ORT, LAND FROM ALT_STANDORT WHERE VERTRAG_ID = ? AND DATENSTAND = ?";
    }
    @Override protected StandortAlt lese(ResultSet rs) throws SQLException {
        return new StandortAlt(
                rs.getString("STRASSE"),
                rs.getString("HAUSNUMMER"),
                rs.getString("PLZ"),
                rs.getString("ORT"),
                rs.getString("LAND")
        );
    }
    @Override protected void uebernehme(QuellDatensatz datensatz, StandortAlt wert) {
        datensatz.setStandort(wert);
    }
}
