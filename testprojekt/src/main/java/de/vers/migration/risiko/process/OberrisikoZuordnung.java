package de.vers.migration.risiko.process;

import de.vers.migration.risiko.model.AltRisikoDto;
import org.springframework.stereotype.Component;

/**
 * Ordnet jedem Risiko ein Oberrisiko zu: Gebäude oder Hausrat.
 * (Diese Datei ist absichtlich Windows-1252-kodiert – Umlaute müssen trotzdem korrekt ankommen.)
 */
@Component
public class OberrisikoZuordnung {

    public String ermittle(AltRisikoDto risiko) {
        String code = risiko.getOberrisikoCode();
        if (code == null || code.isBlank()) {
            // Altbestand vor 2005: kein Oberrisiko gepflegt -> Gebäude
            return "GEBAEUDE";
        }
        return switch (code) {
            case "G" -> "GEBAEUDE";
            case "H" -> "HAUSRAT";
            default -> throw new IllegalArgumentException("Unbekanntes Oberrisiko: " + code);
        };
    }
}
