package de.vers.migration.risiko.anreicherung;

import de.vers.migration.risiko.model.AltRisikoDto;
import org.springframework.stereotype.Component;

/**
 * Reichert das gelesene Risiko um die Bauartklasse aus dem Objektbestand an.
 * Wird beim Einlesen je Risiko aufgerufen und gehört zur Read-Phase.
 */
@Component
public class RisikoDeckungsAnreicherer {

    public void reichereAn(AltRisikoDto risiko) {
        if (risiko.getBauartklasse() == null || risiko.getBauartklasse().isBlank()) {
            risiko.setBauartklasse("BAK1"); // Default laut Altsystem: massive Bauweise
        }
    }
}
