package de.vers.migration.risiko;

/** Liegt im Package des Themas, erwähnt es aber nirgends sonst. */
public final class Pruefziffer {

    private Pruefziffer() {
    }

    public static boolean gueltig(String vertragsnummer) {
        int summe = 0;
        for (char c : vertragsnummer.toCharArray()) {
            summe += Character.getNumericValue(c);
        }
        return summe % 10 == 0;
    }
}
