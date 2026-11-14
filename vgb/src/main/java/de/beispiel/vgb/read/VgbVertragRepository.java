package de.beispiel.vgb.read;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Erste Bestandsabfrage; Schwebe und weitere Entitaeten muessen erst spezifiziert werden. */
@Repository
public class VgbVertragRepository {
    private final JdbcTemplate jdbc;
    public VgbVertragRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record VgbVertragAlt(long vertragId, String vertragsnummer, String tarif,
                               LocalDate beginn, String datenstand) {}
    public List<VgbVertragAlt> leseBestand() {
        return jdbc.query("SELECT VERTRAG_ID, VERTRAGSNUMMER, TARIF, BEGINN, DATENSTAND "
                        + "FROM ALT_VGBVERTRAG WHERE DATENSTAND='BESTAND' ORDER BY VERTRAG_ID",
                (rs, nr) -> new VgbVertragAlt(rs.getLong("VERTRAG_ID"),
                        rs.getString("VERTRAGSNUMMER"), rs.getString("TARIF"),
                        rs.getObject("BEGINN", LocalDate.class), rs.getString("DATENSTAND")));
    }
    // TODO [OFFEN] VGB-Read-Delegator sowie 13 x Bestand-/Schwebe-Anreicherer
}
