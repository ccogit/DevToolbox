package de.beispiel.vhv.modell.alt;

import java.math.BigDecimal;

/** Wertsachen: DB2-Altdatenmodell vor fachlicher Normalisierung. */
public record WertsachenAlt(
        String kategorie,
        BigDecimal einzelwert,
        Integer anzahl,
        String aufbewahrung,
        BigDecimal grenze
) { }
