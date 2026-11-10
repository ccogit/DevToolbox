package de.vers.migration.risiko.read;

import de.vers.migration.risiko.model.AltRisikoDto;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Liest alle aktiven Risiken des Produkts WG2010, sortiert nach Vertrag. */
@Component
public class AltRisikoItemReader extends JdbcCursorItemReader<AltRisikoDto> {

    public AltRisikoItemReader(DataSource altDb, AltRisikoRowMapper rowMapper) throws IOException {
        setName("altRisikoReader");
        setDataSource(altDb);
        setSql(new ClassPathResource("sql/select_alt_risiken.sql").getContentAsString(StandardCharsets.UTF_8));
        setRowMapper(rowMapper);
        setFetchSize(1000);
    }
}
