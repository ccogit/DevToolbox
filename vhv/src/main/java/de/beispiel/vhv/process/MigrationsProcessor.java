package de.beispiel.vhv.process;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import de.beispiel.vhv.modell.*;

/** Spring-Batch-Process: Vertrag inklusive aller zugeordneten Entitaeten. */
@Component
public final class MigrationsProcessor implements ItemProcessor<QuellDatensatz, ZielDatensatz> {
    private final MappingDelegator delegator;
    public MigrationsProcessor(MappingDelegator delegator) { this.delegator = delegator; }
    @Override public ZielDatensatz process(QuellDatensatz item) {
        return delegator.mappe(item);
    }
}
