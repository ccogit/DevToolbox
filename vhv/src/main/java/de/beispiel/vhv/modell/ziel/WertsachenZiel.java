package de.beispiel.vhv.modell.ziel;

import java.math.BigDecimal;

/** Wertsachen: kanonisches Zielmodell nach Mapping. */
public record WertsachenZiel(
        String kategorie,
        BigDecimal einzelwert,
        Integer anzahl,
        String aufbewahrung,
        BigDecimal grenze
) { }
