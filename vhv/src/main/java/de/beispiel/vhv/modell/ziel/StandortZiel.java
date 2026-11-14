package de.beispiel.vhv.modell.ziel;



/** Standort: kanonisches Zielmodell nach Mapping. */
public record StandortZiel(
        String strasse,
        String hausnummer,
        String plz,
        String ort,
        String land
) { }
