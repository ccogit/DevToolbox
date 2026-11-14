package de.beispiel.vhv.read.anreicherer;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.alt.VertragAlt;

/** Entitaetsabfrage mit expliziten Spalten; beide Datenstaende nutzen dieselbe Quellstruktur. */
abstract class BasisVertragAnreicherer extends AbstrakterJdbcAnreicherer<VertragAlt> {
    protected BasisVertragAnreicherer(JdbcTemplate jdbc, Datenstand datenstand) {
        super(jdbc, datenstand);
    }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.VERTRAG; }
    @Override protected String sql() {
        return "SELECT VERTRAGSNUMMER, TARIF, BEGINN, ABLAUF, ZAHLWEISE FROM ALT_VERTRAG WHERE VERTRAG_ID = ? AND DATENSTAND = ?";
    }
    @Override protected VertragAlt lese(ResultSet rs) throws SQLException {
        return new VertragAlt(
                rs.getString("VERTRAGSNUMMER"),
                rs.getString("TARIF"),
                rs.getObject("BEGINN", java.time.LocalDate.class),
                rs.getObject("ABLAUF", java.time.LocalDate.class),
                rs.getString("ZAHLWEISE")
        );
    }
    @Override protected void uebernehme(QuellDatensatz datensatz, VertragAlt wert) {
        datensatz.setVertrag(wert);
    }
}
