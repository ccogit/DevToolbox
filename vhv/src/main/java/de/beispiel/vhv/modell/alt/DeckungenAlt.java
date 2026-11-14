package de.beispiel.vhv.modell.alt;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Deckungen: DB2-Altdatenmodell vor fachlicher Normalisierung. */
public record DeckungenAlt(
        String deckungsart,
        BigDecimal summe,
        BigDecimal selbstbeteiligung,
        String einschluss,
        LocalDate gueltigAb
) { }
