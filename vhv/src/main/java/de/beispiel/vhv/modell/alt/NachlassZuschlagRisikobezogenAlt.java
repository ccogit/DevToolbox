package de.beispiel.vhv.modell.alt;

import java.math.BigDecimal;
import java.time.LocalDate;

/** NachlassZuschlagRisikobezogen: DB2-Altdatenmodell vor fachlicher Normalisierung. */
public record NachlassZuschlagRisikobezogenAlt(
        String merkmal,
        BigDecimal prozentsatz,
        BigDecimal betrag,
        LocalDate gueltigAb,
        String grund
) { }
