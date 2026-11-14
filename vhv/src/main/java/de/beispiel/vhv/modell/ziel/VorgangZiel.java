package de.beispiel.vhv.modell.ziel;

import java.time.LocalDate;

/** Vorgang: kanonisches Zielmodell nach Mapping. */
public record VorgangZiel(
        String vorgangsnummer,
        String vorgangsart,
        LocalDate eingang,
        String bearbeitungsstatus,
        Integer prioritaet
) { }
