package de.beispiel.vhv.modell.alt;

import java.time.LocalDate;

/** Vorgang: DB2-Altdatenmodell vor fachlicher Normalisierung. */
public record VorgangAlt(
        String vorgangsnummer,
        String vorgangsart,
        LocalDate eingang,
        String bearbeitungsstatus,
        Integer prioritaet
) { }
