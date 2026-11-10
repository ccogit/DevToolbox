package de.vers.migration.common;

/** Zahlweise im Neusystem. */
public enum Zahlweise {
    JAEHRLICH(1), HALBJAEHRLICH(2), VIERTELJAEHRLICH(4), MONATLICH(12);

    private final int raten;

    Zahlweise(int raten) {
        this.raten = raten;
    }

    public int raten() {
        return raten;
    }

    /** Altsystem-Codes: J, H, V, M. */
    public static Zahlweise ausAltcode(String code) {
        return switch (code) {
            case "J" -> JAEHRLICH;
            case "H" -> HALBJAEHRLICH;
            case "V" -> VIERTELJAEHRLICH;
            case "M" -> MONATLICH;
            default -> throw new IllegalArgumentException("Unbekannte Zahlweise: " + code);
        };
    }
}
