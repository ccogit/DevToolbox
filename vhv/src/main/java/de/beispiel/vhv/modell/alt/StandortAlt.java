package de.beispiel.vhv.modell.alt;



/** Standort: DB2-Altdatenmodell vor fachlicher Normalisierung. */
public record StandortAlt(
        String strasse,
        String hausnummer,
        String plz,
        String ort,
        String land
) { }
