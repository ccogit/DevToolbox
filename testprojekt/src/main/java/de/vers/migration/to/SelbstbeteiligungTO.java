package de.vers.migration.to;

import java.math.BigDecimal;

/** Ebene 3 – ohne Suchbegriff, nur über die verschachtelte Struktur erreichbar. */
public record SelbstbeteiligungTO(BigDecimal betrag, boolean prozentual) {
}
