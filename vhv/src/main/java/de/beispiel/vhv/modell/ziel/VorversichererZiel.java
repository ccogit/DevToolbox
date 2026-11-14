package de.beispiel.vhv.modell.ziel;

import java.time.LocalDate;

/** Vorversicherer: kanonisches Zielmodell nach Mapping. */
public record VorversichererZiel(
        String gesellschaft,
        String vertragsnummerAlt,
        LocalDate schadenfreiSeit,
        LocalDate vorvertragEnde,
        Integer schadenzahl
) { }
