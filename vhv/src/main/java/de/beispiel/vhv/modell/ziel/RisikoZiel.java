package de.beispiel.vhv.modell.ziel;

import java.math.BigDecimal;

/** Risiko: kanonisches Zielmodell nach Mapping. */
public record RisikoZiel(
        String risikonummer,
        String risikoart,
        BigDecimal versicherungssumme,
        String tarifierungszone,
        BigDecimal selbstbehalt
) { }
