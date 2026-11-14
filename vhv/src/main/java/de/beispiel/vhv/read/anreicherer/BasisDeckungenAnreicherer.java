package de.beispiel.vhv.read.anreicherer;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.alt.DeckungenAlt;

/** Entitaetsabfrage mit expliziten Spalten; beide Datenstaende nutzen dieselbe Quellstruktur. */
abstract class BasisDeckungenAnreicherer extends AbstrakterJdbcAnreicherer<DeckungenAlt> {
    protected BasisDeckungenAnreicherer(JdbcTemplate jdbc, Datenstand datenstand) {
        super(jdbc, datenstand);
    }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.DECKUNGEN; }
    @Override protected String sql() {
        return "SELECT DECKUNGSART, SUMME, SELBSTBETEILIGUNG, EINSCHLUSS, GUELTIG_AB FROM ALT_DECKUNGEN WHERE VERTRAG_ID = ? AND DATENSTAND = ?";
    }
    @Override protected DeckungenAlt lese(ResultSet rs) throws SQLException {
        return new DeckungenAlt(
                rs.getString("DECKUNGSART"),
                rs.getBigDecimal("SUMME"),
                rs.getBigDecimal("SELBSTBETEILIGUNG"),
                rs.getString("EINSCHLUSS"),
                rs.getObject("GUELTIG_AB", java.time.LocalDate.class)
        );
    }
    @Override protected void uebernehme(QuellDatensatz datensatz, DeckungenAlt wert) {
        datensatz.setDeckungen(wert);
    }
}
