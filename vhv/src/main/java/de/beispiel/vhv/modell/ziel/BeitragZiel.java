package de.beispiel.vhv.modell.ziel;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Beitrag: kanonisches Zielmodell nach Mapping. */
public record BeitragZiel(
        BigDecimal bruttobeitrag,
        BigDecimal nettobeitrag,
        BigDecimal versicherungsteuer,
        String zahlweise,
        LocalDate faelligkeit
) { }
