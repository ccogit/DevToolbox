package de.beispiel.vhv.modell.alt;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Beitrag: DB2-Altdatenmodell vor fachlicher Normalisierung. */
public record BeitragAlt(
        BigDecimal bruttobeitrag,
        BigDecimal nettobeitrag,
        BigDecimal versicherungsteuer,
        String zahlweise,
        LocalDate faelligkeit
) { }
