package de.beispiel.vhv.read.anreicherer;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.alt.RisikoAlt;

/** Entitaetsabfrage mit expliziten Spalten; beide Datenstaende nutzen dieselbe Quellstruktur. */
abstract class BasisRisikoAnreicherer extends AbstrakterJdbcAnreicherer<RisikoAlt> {
    protected BasisRisikoAnreicherer(JdbcTemplate jdbc, Datenstand datenstand) {
        super(jdbc, datenstand);
    }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.RISIKO; }
    @Override protected String sql() {
        return "SELECT RISIKONUMMER, RISIKOART, VERSICHERUNGSSUMME, TARIFIERUNGSZONE, SELBSTBEHALT FROM ALT_RISIKO WHERE VERTRAG_ID = ? AND DATENSTAND = ?";
    }
    @Override protected RisikoAlt lese(ResultSet rs) throws SQLException {
        return new RisikoAlt(
                rs.getString("RISIKONUMMER"),
                rs.getString("RISIKOART"),
                rs.getBigDecimal("VERSICHERUNGSSUMME"),
                rs.getString("TARIFIERUNGSZONE"),
                rs.getBigDecimal("SELBSTBEHALT")
        );
    }
    @Override protected void uebernehme(QuellDatensatz datensatz, RisikoAlt wert) {
        datensatz.setRisiko(wert);
    }
}
