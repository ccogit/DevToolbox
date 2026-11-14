package de.beispiel.vhv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import de.beispiel.vhv.process.mapper.GemeinsameSubMapper;

class GemeinsameSubMapperTest {
    private final GemeinsameSubMapper mapper = new GemeinsameSubMapper();
    @Test void betraegeRundenKaufmaennisch() {
        assertThat(mapper.geld(new BigDecimal("123.456"))).isEqualByComparingTo("123.46");
    }
    @Test void plzBleibtEinStringUndCodesWerdenNormiert() {
        assertThat(mapper.text(" 04567 ")).isEqualTo("04567");
        assertThat(mapper.code(" ab ")).isEqualTo("AB");
    }
    @Test void grenzenWerdenValidiert() {
        assertThatThrownBy(() -> mapper.prozent(new BigDecimal("101"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.nichtNegativ(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }
}
