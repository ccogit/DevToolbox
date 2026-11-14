package de.beispiel.vhv.modell.alt;

import java.math.BigDecimal;
import java.time.LocalDate;

/** NachlassZuschlagVertragsbezogen: DB2-Altdatenmodell vor fachlicher Normalisierung. */
public record NachlassZuschlagVertragsbezogenAlt(
        String merkmal,
        BigDecimal prozentsatz,
        BigDecimal betrag,
        LocalDate gueltigAb,
        String grund
) { }
