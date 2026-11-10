package de.vers.migration.risiko.read;

import de.vers.migration.model.Adresse;
import de.vers.migration.model.AltVertragDto;
import de.vers.migration.risiko.model.AltRisikoDto;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import static de.vers.migration.common.Konstanten.PRODUKT_WG;

@Component
public class AltRisikoRowMapper implements RowMapper<AltRisikoDto> {

    private final AltBestandDao dao;

    public AltRisikoRowMapper(AltBestandDao dao) {
        this.dao = dao;
    }

    @Override
    public AltRisikoDto mapRow(ResultSet rs, int rowNum) throws SQLException {
        AltVertragDto vertrag = new AltVertragDto();
        vertrag.setVertragsnummer(rs.getString("VNR"));
        vertrag.setProduktschluessel(PRODUKT_WG);
        vertrag.setTarif(rs.getString("TARIF"));
        vertrag.setZahlweiseCode(rs.getString("ZW"));
        vertrag.setStatus(rs.getString("STATUS"));
        vertrag.setBeginn(rs.getDate("BEGINN").toLocalDate());

        AltRisikoDto risiko = new AltRisikoDto();
        risiko.setVertrag(vertrag);
        risiko.setRisikoart(rs.getString("RISART").trim());
        risiko.setOberrisikoCode(rs.getString("OBERRIS"));
        risiko.setBauartklasse(rs.getString("BAK"));
        risiko.setBeitrag(rs.getBigDecimal("JAHRESBEITRAG"));

        // Versicherungssumme fehlt bei Altverträgen vor 2005 und wird über die Wohnfläche nachgelesen
        BigDecimal vs = rs.getBigDecimal("VS");
        if (vs == null) {
            vs = dao.ermittleSummeAusWohnflaeche(vertrag.getVertragsnummer());
        }
        risiko.setVersicherungssumme(vs);

        Adresse lage = new Adresse();
        lage.setStrasse(rs.getString("STR"));
        lage.setPlz(rs.getString("PLZ"));
        lage.setOrt(rs.getString("ORT"));
        risiko.setLage(lage);
        return risiko;
    }
}
