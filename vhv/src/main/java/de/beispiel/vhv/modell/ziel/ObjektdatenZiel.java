package de.beispiel.vhv.modell.ziel;

import java.math.BigDecimal;

/** Objektdaten: kanonisches Zielmodell nach Mapping. */
public record ObjektdatenZiel(
        String objektart,
        BigDecimal wohnflaeche,
        Integer baujahr,
        String bauartklasse,
        String nutzung
) { }
