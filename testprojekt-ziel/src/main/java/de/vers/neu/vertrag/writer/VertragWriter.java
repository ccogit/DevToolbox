package de.vers.neu.vertrag.writer;

import de.vers.neu.vertrag.entity.VertragEntity;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import jakarta.persistence.EntityManager;

/** Schreibt Verträge ins Neusystem (Write-Phase). */
public class VertragWriter implements ItemWriter<VertragEntity> {

    private final EntityManager neuDb;

    public VertragWriter(EntityManager neuDb) {
        this.neuDb = neuDb;
    }

    @Override
    public void write(Chunk<? extends VertragEntity> chunk) {
        chunk.forEach(neuDb::persist);
    }
}
