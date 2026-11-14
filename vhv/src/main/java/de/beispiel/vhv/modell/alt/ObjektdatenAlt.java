package de.beispiel.vhv.modell.alt;

import java.math.BigDecimal;

/** Objektdaten: DB2-Altdatenmodell vor fachlicher Normalisierung. */
public record ObjektdatenAlt(
        String objektart,
        BigDecimal wohnflaeche,
        Integer baujahr,
        String bauartklasse,
        String nutzung
) { }
