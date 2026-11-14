package de.beispiel.vhv.process.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Teilregeln, die entitaetsspezifische Sub-Mapper wiederverwenden. */
@Component
public class GemeinsameSubMapper {
    public String text(String s) { return s == null ? null : s.trim(); }
    public String code(String s) { return s == null ? null : s.trim().toUpperCase(Locale.ROOT); }
    public BigDecimal geld(BigDecimal n) { return n == null ? null : n.setScale(2, RoundingMode.HALF_UP); }
    public BigDecimal prozent(BigDecimal n) {
        if (n != null && n.abs().compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("Unzulaessiger Nachlass/Zuschlag > 100%");
        return geld(n);
    }
    public BigDecimal nichtNegativ(BigDecimal n) {
        if (n != null && n.signum() < 0) throw new IllegalArgumentException("Negativer Versicherungswert");
        return geld(n);
    }
}
