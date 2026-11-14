package de.beispiel.vhv.read.anreicherer;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.alt.VorversichererAlt;

/** Entitaetsabfrage mit expliziten Spalten; beide Datenstaende nutzen dieselbe Quellstruktur. */
abstract class BasisVorversichererAnreicherer extends AbstrakterJdbcAnreicherer<VorversichererAlt> {
    protected BasisVorversichererAnreicherer(JdbcTemplate jdbc, Datenstand datenstand) {
        super(jdbc, datenstand);
    }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.VORVERSICHERER; }
    @Override protected String sql() {
        return "SELECT GESELLSCHAFT, VERTRAGSNUMMER_ALT, SCHADENFREI_SEIT, VORVERTRAG_ENDE, SCHADENZAHL FROM ALT_VORVERSICHERER WHERE VERTRAG_ID = ? AND DATENSTAND = ?";
    }
    @Override protected VorversichererAlt lese(ResultSet rs) throws SQLException {
        return new VorversichererAlt(
                rs.getString("GESELLSCHAFT"),
                rs.getString("VERTRAGSNUMMER_ALT"),
                rs.getObject("SCHADENFREI_SEIT", java.time.LocalDate.class),
                rs.getObject("VORVERTRAG_ENDE", java.time.LocalDate.class),
                rs.getObject("SCHADENZAHL", Integer.class)
        );
    }
    @Override protected void uebernehme(QuellDatensatz datensatz, VorversichererAlt wert) {
        datensatz.setVorversicherer(wert);
    }
}
