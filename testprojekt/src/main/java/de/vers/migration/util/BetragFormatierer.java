package de.vers.migration.util;

import java.math.BigDecimal;

/** Hilfsklasse ohne Fachbezug – wird von DeckungTO genutzt, darf aber NICHT eingesammelt werden. */
public final class BetragFormatierer {

    private BetragFormatierer() {
    }

    public static String formatiere(BigDecimal betrag) {
        return betrag == null ? "-" : betrag.setScale(2).toPlainString() + " EUR";
    }
}
