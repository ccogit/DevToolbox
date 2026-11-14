package de.beispiel.vhv.read.anreicherer;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.alt.NachlassZuschlagVertragsbezogenAlt;

/** Entitaetsabfrage mit expliziten Spalten; beide Datenstaende nutzen dieselbe Quellstruktur. */
abstract class BasisNachlassZuschlagVertragsbezogenAnreicherer extends AbstrakterJdbcAnreicherer<NachlassZuschlagVertragsbezogenAlt> {
    protected BasisNachlassZuschlagVertragsbezogenAnreicherer(JdbcTemplate jdbc, Datenstand datenstand) {
        super(jdbc, datenstand);
    }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.NACHLASS_ZUSCHLAG_VERTRAGSBEZOGEN; }
    @Override protected String sql() {
        return "SELECT MERKMAL, PROZENTSATZ, BETRAG, GUELTIG_AB, GRUND FROM ALT_NACHLASSZUSCHLAGVERTRAGSBEZOGEN WHERE VERTRAG_ID = ? AND DATENSTAND = ?";
    }
    @Override protected NachlassZuschlagVertragsbezogenAlt lese(ResultSet rs) throws SQLException {
        return new NachlassZuschlagVertragsbezogenAlt(
                rs.getString("MERKMAL"),
                rs.getBigDecimal("PROZENTSATZ"),
                rs.getBigDecimal("BETRAG"),
                rs.getObject("GUELTIG_AB", java.time.LocalDate.class),
                rs.getString("GRUND")
        );
    }
    @Override protected void uebernehme(QuellDatensatz datensatz, NachlassZuschlagVertragsbezogenAlt wert) {
        datensatz.setNachlassZuschlagVertragsbezogen(wert);
    }
}
