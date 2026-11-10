package de.vers.migration.risiko.anreicherung;

import de.vers.migration.risiko.model.AltRisikoDto;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Reichert das gelesene Risiko um die Bauartklasse aus dem Objektbestand an.
 * Implementiert ItemProcessor, liegt aber im Verzeichnis "anreicherung" und gehört deshalb zur Read-Phase.
 */
@Component
public class RisikoDeckungsAnreicherer implements ItemProcessor<AltRisikoDto, AltRisikoDto> {

    @Override
    public AltRisikoDto process(AltRisikoDto risiko) {
        if (risiko.getBauartklasse() == null || risiko.getBauartklasse().isBlank()) {
            risiko.setBauartklasse("BAK1"); // Default laut Altsystem: massive Bauweise
        }
        return risiko;
    }
}
