package de.vers.neu.vertrag.writer;

import de.vers.neu.vertrag.entity.VertragEntity;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

/** Schreibt Verträge ins Neusystem (Write-Phase). */
public class VertragWriter implements ItemWriter<VertragEntity> {

    private final VertragRepository repository;

    public VertragWriter(VertragRepository repository) {
        this.repository = repository;
    }

    @Override
    public void write(Chunk<? extends VertragEntity> chunk) {
        repository.saveAll(chunk.getItems());
    }
}
