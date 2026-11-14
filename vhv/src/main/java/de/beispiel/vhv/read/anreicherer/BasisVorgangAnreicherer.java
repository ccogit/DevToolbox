package de.beispiel.vhv.read.anreicherer;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.alt.VorgangAlt;

/** Entitaetsabfrage mit expliziten Spalten; beide Datenstaende nutzen dieselbe Quellstruktur. */
abstract class BasisVorgangAnreicherer extends AbstrakterJdbcAnreicherer<VorgangAlt> {
    protected BasisVorgangAnreicherer(JdbcTemplate jdbc, Datenstand datenstand) {
        super(jdbc, datenstand);
    }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.VORGANG; }
    @Override protected String sql() {
        return "SELECT VORGANGSNUMMER, VORGANGSART, EINGANG, BEARBEITUNGSSTATUS, PRIORITAET FROM ALT_VORGANG WHERE VERTRAG_ID = ? AND DATENSTAND = ?";
    }
    @Override protected VorgangAlt lese(ResultSet rs) throws SQLException {
        return new VorgangAlt(
                rs.getString("VORGANGSNUMMER"),
                rs.getString("VORGANGSART"),
                rs.getObject("EINGANG", java.time.LocalDate.class),
                rs.getString("BEARBEITUNGSSTATUS"),
                rs.getObject("PRIORITAET", Integer.class)
        );
    }
    @Override protected void uebernehme(QuellDatensatz datensatz, VorgangAlt wert) {
        datensatz.setVorgang(wert);
    }
}
