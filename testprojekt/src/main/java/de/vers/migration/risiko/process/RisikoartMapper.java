package de.vers.migration.risiko.process;

import org.springframework.stereotype.Component;

/** Umsetzung der Risikoart-Codes Alt -> Neu. (Datei hat absichtlich Windows-Zeilenenden.) */
@Component
public class RisikoartMapper {

    public String mappe(String altcode) {
        return switch (altcode) {
            case "FE" -> "FEUER";
            case "LW" -> "LEITUNGSWASSER";
            case "ST" -> "STURM_HAGEL";
            case "EL" -> "ELEMENTAR";
            case "GL" -> "GLAS";          // im Neuprodukt evtl. nicht mehr angeboten
            default -> throw new IllegalArgumentException("Unbekannte Risikoart: " + altcode);
        };
    }
}
