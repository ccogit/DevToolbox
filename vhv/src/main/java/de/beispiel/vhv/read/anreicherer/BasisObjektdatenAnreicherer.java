package de.beispiel.vhv.read.anreicherer;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.alt.ObjektdatenAlt;

/** Entitaetsabfrage mit expliziten Spalten; beide Datenstaende nutzen dieselbe Quellstruktur. */
abstract class BasisObjektdatenAnreicherer extends AbstrakterJdbcAnreicherer<ObjektdatenAlt> {
    protected BasisObjektdatenAnreicherer(JdbcTemplate jdbc, Datenstand datenstand) {
        super(jdbc, datenstand);
    }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.OBJEKTDATEN; }
    @Override protected String sql() {
        return "SELECT OBJEKTART, WOHNFLAECHE, BAUJAHR, BAUARTKLASSE, NUTZUNG FROM ALT_OBJEKTDATEN WHERE VERTRAG_ID = ? AND DATENSTAND = ?";
    }
    @Override protected ObjektdatenAlt lese(ResultSet rs) throws SQLException {
        return new ObjektdatenAlt(
                rs.getString("OBJEKTART"),
                rs.getBigDecimal("WOHNFLAECHE"),
                rs.getObject("BAUJAHR", Integer.class),
                rs.getString("BAUARTKLASSE"),
                rs.getString("NUTZUNG")
        );
    }
    @Override protected void uebernehme(QuellDatensatz datensatz, ObjektdatenAlt wert) {
        datensatz.setObjektdaten(wert);
    }
}
