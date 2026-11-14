package de.beispiel.vhv.modell.alt;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Wertermittlung: DB2-Altdatenmodell vor fachlicher Normalisierung. */
public record WertermittlungAlt(
        String ermittlungsart,
        BigDecimal neuwert,
        Integer indexjahr,
        BigDecimal indexfaktor,
        LocalDate stand
) { }
