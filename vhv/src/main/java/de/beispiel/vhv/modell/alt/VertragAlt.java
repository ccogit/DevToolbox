package de.beispiel.vhv.modell.alt;

import java.time.LocalDate;

/** Vertrag: DB2-Altdatenmodell vor fachlicher Normalisierung. */
public record VertragAlt(
        String vertragsnummer,
        String tarif,
        LocalDate beginn,
        LocalDate ablauf,
        String zahlweise
) { }
