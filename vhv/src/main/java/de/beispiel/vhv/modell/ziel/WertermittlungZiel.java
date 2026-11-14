package de.beispiel.vhv.modell.ziel;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Wertermittlung: kanonisches Zielmodell nach Mapping. */
public record WertermittlungZiel(
        String ermittlungsart,
        BigDecimal neuwert,
        Integer indexjahr,
        BigDecimal indexfaktor,
        LocalDate stand
) { }
