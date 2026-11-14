package de.beispiel.vhv.modell.ziel;

import java.math.BigDecimal;
import java.time.LocalDate;

/** NachlassZuschlagVertragsbezogen: kanonisches Zielmodell nach Mapping. */
public record NachlassZuschlagVertragsbezogenZiel(
        String merkmal,
        BigDecimal prozentsatz,
        BigDecimal betrag,
        LocalDate gueltigAb,
        String grund
) { }
