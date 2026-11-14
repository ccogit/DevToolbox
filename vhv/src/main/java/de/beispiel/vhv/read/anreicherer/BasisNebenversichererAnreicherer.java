package de.beispiel.vhv.read.anreicherer;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.alt.NebenversichererAlt;

/** Entitaetsabfrage mit expliziten Spalten; beide Datenstaende nutzen dieselbe Quellstruktur. */
abstract class BasisNebenversichererAnreicherer extends AbstrakterJdbcAnreicherer<NebenversichererAlt> {
    protected BasisNebenversichererAnreicherer(JdbcTemplate jdbc, Datenstand datenstand) {
        super(jdbc, datenstand);
    }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.NEBENVERSICHERER; }
    @Override protected String sql() {
        return "SELECT GESELLSCHAFT, VERTRAGSNUMMER_FREMD, ANTEIL_PROZENT, DECKUNGSBEREICH, BEGINN FROM ALT_NEBENVERSICHERER WHERE VERTRAG_ID = ? AND DATENSTAND = ?";
    }
    @Override protected NebenversichererAlt lese(ResultSet rs) throws SQLException {
        return new NebenversichererAlt(
                rs.getString("GESELLSCHAFT"),
                rs.getString("VERTRAGSNUMMER_FREMD"),
                rs.getBigDecimal("ANTEIL_PROZENT"),
                rs.getString("DECKUNGSBEREICH"),
                rs.getObject("BEGINN", java.time.LocalDate.class)
        );
    }
    @Override protected void uebernehme(QuellDatensatz datensatz, NebenversichererAlt wert) {
        datensatz.setNebenversicherer(wert);
    }
}
