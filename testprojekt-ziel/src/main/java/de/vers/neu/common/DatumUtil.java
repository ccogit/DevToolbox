package de.vers.neu.common;

import java.time.LocalDate;

/** Hilfsklasse ohne Phasenbezug - darf keine Vorlage werden. */
public final class DatumUtil {

    private DatumUtil() {
    }

    public static LocalDate oderHeute(LocalDate datum) {
        return datum == null ? LocalDate.now() : datum;
    }
}
