package de.beispiel.vhv.modell.ziel;

import java.time.LocalDate;

/** Vertrag: kanonisches Zielmodell nach Mapping. */
public record VertragZiel(
        String vertragsnummer,
        String tarif,
        LocalDate beginn,
        LocalDate ablauf,
        String zahlweise
) { }
