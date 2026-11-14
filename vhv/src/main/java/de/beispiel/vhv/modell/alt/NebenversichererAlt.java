package de.beispiel.vhv.modell.alt;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Nebenversicherer: DB2-Altdatenmodell vor fachlicher Normalisierung. */
public record NebenversichererAlt(
        String gesellschaft,
        String vertragsnummerFremd,
        BigDecimal anteilProzent,
        String deckungsbereich,
        LocalDate beginn
) { }
