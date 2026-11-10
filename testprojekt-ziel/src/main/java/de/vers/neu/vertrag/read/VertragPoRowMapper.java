package de.vers.neu.vertrag.read;

import de.vers.neu.vertrag.po.VertragPO;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Liest eine Zeile aus ALT_VERTRAG in das PO; die SQL liegt in sql/read/vertrag.sql. */
public class VertragPoRowMapper implements RowMapper<VertragPO> {

    @Override
    public VertragPO mapRow(ResultSet rs, int rowNum) throws SQLException {
        VertragPO po = new VertragPO();
        po.setVnr(rs.getString("VNR").trim());
        po.setProdukt(rs.getString("PRODUKT"));
        po.setTarif(rs.getString("TARIF"));
        po.setZw(rs.getString("ZW"));
        po.setBeginn(rs.getDate("BEGINN").toLocalDate());
        po.setJahresbeitrag(rs.getBigDecimal("JAHRESBEITRAG"));
        return po;
    }
}
