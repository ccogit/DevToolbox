package de.beispiel.vhv.read.anreicherer;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.alt.BeitragAlt;

/** Entitaetsabfrage mit expliziten Spalten; beide Datenstaende nutzen dieselbe Quellstruktur. */
abstract class BasisBeitragAnreicherer extends AbstrakterJdbcAnreicherer<BeitragAlt> {
    protected BasisBeitragAnreicherer(JdbcTemplate jdbc, Datenstand datenstand) {
        super(jdbc, datenstand);
    }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.BEITRAG; }
    @Override protected String sql() {
        return "SELECT BRUTTOBEITRAG, NETTOBEITRAG, VERSICHERUNGSTEUER, ZAHLWEISE, FAELLIGKEIT FROM ALT_BEITRAG WHERE VERTRAG_ID = ? AND DATENSTAND = ?";
    }
    @Override protected BeitragAlt lese(ResultSet rs) throws SQLException {
        return new BeitragAlt(
                rs.getBigDecimal("BRUTTOBEITRAG"),
                rs.getBigDecimal("NETTOBEITRAG"),
                rs.getBigDecimal("VERSICHERUNGSTEUER"),
                rs.getString("ZAHLWEISE"),
                rs.getObject("FAELLIGKEIT", java.time.LocalDate.class)
        );
    }
    @Override protected void uebernehme(QuellDatensatz datensatz, BeitragAlt wert) {
        datensatz.setBeitrag(wert);
    }
}
