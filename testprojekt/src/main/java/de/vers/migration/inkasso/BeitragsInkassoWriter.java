package de.vers.migration.inkasso;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

/** Anderes Thema (Inkasso). Erwähnt den Begriff nur in einem Kommentar -> erwarteter Fehltreffer. */
public class BeitragsInkassoWriter implements ItemWriter<String> {

    @Override
    public void write(Chunk<? extends String> chunk) {
        // TODO Risk: Rundungsdifferenzen bei Ratenzahlung prüfen
    }
}
