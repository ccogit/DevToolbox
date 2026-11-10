package de.vers.migration.process;

import de.vers.migration.common.Zahlweise;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Rechnet Jahresbeiträge in Ratenbeiträge um. Ohne Suchbegriff - Referenz der Process-Phase. */
@Component
public class BeitragsConverter {

    /** Ratenzuschlag (Altprodukt): halbjährlich 3 %, vierteljährlich und monatlich 5 %. */
    public BigDecimal ratenbeitrag(BigDecimal jahresbeitrag, Zahlweise zahlweise) {
        BigDecimal zuschlag = switch (zahlweise) {
            case JAEHRLICH -> BigDecimal.ONE;
            case HALBJAEHRLICH -> new BigDecimal("1.03");
            case VIERTELJAEHRLICH, MONATLICH -> new BigDecimal("1.05");
        };
        return jahresbeitrag.multiply(zuschlag)
                .divide(BigDecimal.valueOf(zahlweise.raten()), 2, RoundingMode.HALF_UP);
    }
}
