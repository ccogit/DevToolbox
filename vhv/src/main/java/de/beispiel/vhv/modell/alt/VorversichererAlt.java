package de.beispiel.vhv.modell.alt;

import java.time.LocalDate;

/** Vorversicherer: DB2-Altdatenmodell vor fachlicher Normalisierung. */
public record VorversichererAlt(
        String gesellschaft,
        String vertragsnummerAlt,
        LocalDate schadenfreiSeit,
        LocalDate vorvertragEnde,
        Integer schadenzahl
) { }
