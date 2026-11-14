package de.beispiel.vhv.modell.ziel;

import java.math.BigDecimal;
import java.time.LocalDate;

/** NachlassZuschlagRisikobezogen: kanonisches Zielmodell nach Mapping. */
public record NachlassZuschlagRisikobezogenZiel(
        String merkmal,
        BigDecimal prozentsatz,
        BigDecimal betrag,
        LocalDate gueltigAb,
        String grund
) { }
