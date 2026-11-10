package de.vers.neu.{{entitaet}}.read;

import de.vers.neu.{{entitaet}}.po.{{Entitaet}}PO;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Liest eine Zeile aus ALT_{{ENTITAET}} in das PO; die SQL liegt in sql/read/{{entitaet}}.sql. */
public class {{Entitaet}}PoRowMapper implements RowMapper<{{Entitaet}}PO> {

    @Override
    public {{Entitaet}}PO mapRow(ResultSet rs, int rowNum) throws SQLException {
        {{Entitaet}}PO po = new {{Entitaet}}PO();
        po.setVnr(rs.getString("VNR").trim());
        po.setProdukt(rs.getString("PRODUKT"));
        po.setTarif(rs.getString("TARIF"));
        po.setZw(rs.getString("ZW"));
        po.setBeginn(rs.getDate("BEGINN").toLocalDate());
        po.setJahresbeitrag(rs.getBigDecimal("JAHRESBEITRAG"));
        return po;
    }
}
