package de.beispiel.vgb.process.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** Erste VGB-Mappingregel; weitere fachliche Regeln und Sub-Mapper fehlen bewusst. */
@Component
public final class VgbRisikoMapper {
    public record VgbRisikoAlt(String risikonummer, BigDecimal versicherungssumme) {}
    public record VgbRisikoZiel(String id, BigDecimal summe, String gefahrenzone) {}
    public VgbRisikoZiel mappeBestand(VgbRisikoAlt quelle) {
        if (quelle == null) return null;
        BigDecimal summe = quelle.versicherungssumme() == null ? null
                : quelle.versicherungssumme().setScale(2, RoundingMode.HALF_UP);
        // TODO [OFFEN] Geozonierung und Risiko-Sub-Mapper nach VHV-Muster
        return new VgbRisikoZiel(quelle.risikonummer(), summe, null);
    }
    // TODO [OFFEN] Schwebe-Regeln und Einbindung in einen MappingDelegator
}
