package de.beispiel.vhv.modell.alt;

import java.math.BigDecimal;

/** Risiko: DB2-Altdatenmodell vor fachlicher Normalisierung. */
public record RisikoAlt(
        String risikonummer,
        String risikoart,
        BigDecimal versicherungssumme,
        String tarifierungszone,
        BigDecimal selbstbehalt
) { }
