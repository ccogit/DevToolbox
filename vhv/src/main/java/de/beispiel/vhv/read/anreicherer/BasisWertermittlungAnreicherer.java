package de.beispiel.vhv.read.anreicherer;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.alt.WertermittlungAlt;

/** Entitaetsabfrage mit expliziten Spalten; beide Datenstaende nutzen dieselbe Quellstruktur. */
abstract class BasisWertermittlungAnreicherer extends AbstrakterJdbcAnreicherer<WertermittlungAlt> {
    protected BasisWertermittlungAnreicherer(JdbcTemplate jdbc, Datenstand datenstand) {
        super(jdbc, datenstand);
    }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.WERTERMITTLUNG; }
    @Override protected String sql() {
        return "SELECT ERMITTLUNGSART, NEUWERT, INDEXJAHR, INDEXFAKTOR, STAND FROM ALT_WERTERMITTLUNG WHERE VERTRAG_ID = ? AND DATENSTAND = ?";
    }
    @Override protected WertermittlungAlt lese(ResultSet rs) throws SQLException {
        return new WertermittlungAlt(
                rs.getString("ERMITTLUNGSART"),
                rs.getBigDecimal("NEUWERT"),
                rs.getObject("INDEXJAHR", Integer.class),
                rs.getBigDecimal("INDEXFAKTOR"),
                rs.getObject("STAND", java.time.LocalDate.class)
        );
    }
    @Override protected void uebernehme(QuellDatensatz datensatz, WertermittlungAlt wert) {
        datensatz.setWertermittlung(wert);
    }
}
