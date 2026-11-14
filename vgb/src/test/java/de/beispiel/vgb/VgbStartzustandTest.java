package de.beispiel.vgb;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import de.beispiel.vgb.process.mapper.VgbRisikoMapper;
import de.beispiel.vgb.read.VgbVertragRepository;

/** Testet nur das vorhandene Geruest: ein Migrationsjob waere verfrueht. */
@SpringBootTest(properties = "spring.batch.job.enabled=false")
class VgbStartzustandTest {
    @Autowired private VgbVertragRepository vertraege;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private VgbRisikoMapper mapper;
    @Test void hatErsteBestandsdatenUndRisikoRegel() {
        assertThat(vertraege.leseBestand()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ALT_VGBRISIKO", Integer.class)).isEqualTo(2);
        assertThat(mapper.mappeBestand(new VgbRisikoMapper.VgbRisikoAlt("R-1",
            new BigDecimal("123.456"))).summe()).isEqualByComparingTo("123.46");
    }
}
