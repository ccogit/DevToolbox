package de.beispiel.vhv.modell.ziel;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Deckungen: kanonisches Zielmodell nach Mapping. */
public record DeckungenZiel(
        String deckungsart,
        BigDecimal summe,
        BigDecimal selbstbeteiligung,
        String einschluss,
        LocalDate gueltigAb
) { }
