package de.beispiel.vhv.read.anreicherer;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.alt.WertsachenAlt;

/** Entitaetsabfrage mit expliziten Spalten; beide Datenstaende nutzen dieselbe Quellstruktur. */
abstract class BasisWertsachenAnreicherer extends AbstrakterJdbcAnreicherer<WertsachenAlt> {
    protected BasisWertsachenAnreicherer(JdbcTemplate jdbc, Datenstand datenstand) {
        super(jdbc, datenstand);
    }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.WERTSACHEN; }
    @Override protected String sql() {
        return "SELECT KATEGORIE, EINZELWERT, ANZAHL, AUFBEWAHRUNG, GRENZE FROM ALT_WERTSACHEN WHERE VERTRAG_ID = ? AND DATENSTAND = ?";
    }
    @Override protected WertsachenAlt lese(ResultSet rs) throws SQLException {
        return new WertsachenAlt(
                rs.getString("KATEGORIE"),
                rs.getBigDecimal("EINZELWERT"),
                rs.getObject("ANZAHL", Integer.class),
                rs.getString("AUFBEWAHRUNG"),
                rs.getBigDecimal("GRENZE")
        );
    }
    @Override protected void uebernehme(QuellDatensatz datensatz, WertsachenAlt wert) {
        datensatz.setWertsachen(wert);
    }
}
