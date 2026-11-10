package de.vers.migration.kunde;

import org.springframework.batch.item.ItemReader;

/** Anderes Thema (Kunden) - darf im Ergebnis nicht auftauchen. */
public class KundeItemReader implements ItemReader<KundeDto> {

    @Override
    public KundeDto read() {
        return null;
    }
}
