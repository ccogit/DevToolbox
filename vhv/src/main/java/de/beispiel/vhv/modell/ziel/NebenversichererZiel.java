package de.beispiel.vhv.modell.ziel;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Nebenversicherer: kanonisches Zielmodell nach Mapping. */
public record NebenversichererZiel(
        String gesellschaft,
        String vertragsnummerFremd,
        BigDecimal anteilProzent,
        String deckungsbereich,
        LocalDate beginn
) { }
