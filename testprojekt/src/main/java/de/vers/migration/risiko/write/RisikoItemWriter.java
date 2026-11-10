package de.vers.migration.risiko.write;

import de.vers.migration.common.FehlerProtokollWriter;
import de.vers.migration.risiko.model.NeuRisikoEntity;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/** Schreibt die migrierten Risiken; doppelte Risiko-IDs landen im Fehlerprotokoll. */
@Component
public class RisikoItemWriter implements ItemWriter<NeuRisikoEntity> {

    private final NeuRisikoRepository repository;
    private final FehlerProtokollWriter fehlerProtokoll;

    public RisikoItemWriter(NeuRisikoRepository repository, FehlerProtokollWriter fehlerProtokoll) {
        this.repository = repository;
        this.fehlerProtokoll = fehlerProtokoll;
    }

    @Override
    public void write(Chunk<? extends NeuRisikoEntity> chunk) {
        for (NeuRisikoEntity risiko : chunk) {
            if (repository.existsById(risiko.getRisikoId())) {
                fehlerProtokoll.protokolliere(risiko.getVertragsnummer(), "Doppelte ID " + risiko.getRisikoId());
                continue;
            }
            repository.save(risiko);
        }
    }
}
