package de.beispiel.vhv.read.anreicherer;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.modell.alt.NachlassZuschlagRisikobezogenAlt;

/** Entitaetsabfrage mit expliziten Spalten; beide Datenstaende nutzen dieselbe Quellstruktur. */
abstract class BasisNachlassZuschlagRisikobezogenAnreicherer extends AbstrakterJdbcAnreicherer<NachlassZuschlagRisikobezogenAlt> {
    protected BasisNachlassZuschlagRisikobezogenAnreicherer(JdbcTemplate jdbc, Datenstand datenstand) {
        super(jdbc, datenstand);
    }
    @Override public EntitaetTyp entitaet() { return EntitaetTyp.NACHLASS_ZUSCHLAG_RISIKOBEZOGEN; }
    @Override protected String sql() {
        return "SELECT MERKMAL, PROZENTSATZ, BETRAG, GUELTIG_AB, GRUND FROM ALT_NACHLASSZUSCHLAGRISIKOBEZOGEN WHERE VERTRAG_ID = ? AND DATENSTAND = ?";
    }
    @Override protected NachlassZuschlagRisikobezogenAlt lese(ResultSet rs) throws SQLException {
        return new NachlassZuschlagRisikobezogenAlt(
                rs.getString("MERKMAL"),
                rs.getBigDecimal("PROZENTSATZ"),
                rs.getBigDecimal("BETRAG"),
                rs.getObject("GUELTIG_AB", java.time.LocalDate.class),
                rs.getString("GRUND")
        );
    }
    @Override protected void uebernehme(QuellDatensatz datensatz, NachlassZuschlagRisikobezogenAlt wert) {
        datensatz.setNachlassZuschlagRisikobezogen(wert);
    }
}
