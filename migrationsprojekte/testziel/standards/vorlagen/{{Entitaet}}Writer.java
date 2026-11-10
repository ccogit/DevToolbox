package de.vers.neu.{{entitaet}}.writer;

import de.vers.neu.{{entitaet}}.entity.{{Entitaet}}Entity;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import jakarta.persistence.EntityManager;

/** Schreibt Verträge ins Neusystem (Write-Phase). */
public class {{Entitaet}}Writer implements ItemWriter<{{Entitaet}}Entity> {

    private final EntityManager neuDb;

    public {{Entitaet}}Writer(EntityManager neuDb) {
        this.neuDb = neuDb;
    }

    @Override
    public void write(Chunk<? extends {{Entitaet}}Entity> chunk) {
        chunk.forEach(neuDb::persist);
    }
}
