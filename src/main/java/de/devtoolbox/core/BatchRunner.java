package de.devtoolbox.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Runs one job once per entity (e.g. {@code Risiko}, {@code Vertrag}) with otherwise identical values.
 * Each entity gets its own run folder; an error for one entity does not stop the others.
 */
public final class BatchRunner {

    /**
     * @param name        the entity name
     * @param searchTerms comma-separated search terms for the specification job; empty = the name only
     */
    public record Entity(String name, String searchTerms) {
        public Entity(String name) {
            this(name, "");
        }
    }

    private BatchRunner() {
    }

    /**
     * Runs {@code job} for every entity. Specification job ({@link Keys#TERMS} param): terms = search terms or name;
     * otherwise {@link Keys#ENTITY} = name.
     *
     * @param baseValues values common to all runs (entity keys in it are overridden)
     * @return the files of all successful runs, in entity order
     */
    public static List<Path> run(Job job, Map<String, String> baseValues, List<Entity> entities, Consumer<String> log) {
        boolean byTerms = job.params().stream().anyMatch(p -> p.key().equals(Keys.TERMS));
        List<Path> all = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        int done = 0;
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            log.accept("=== Entität " + (i + 1) + " von " + entities.size() + ": " + entity.name() + " ===");
            try {
                all.addAll(job.run(Config.of(job.params(), entityValues(baseValues, entity, byTerms)), log));
                done++;
            } catch (Exception e) {
                failed.add(entity.name());
                log.accept("FEHLER bei Entität " + entity.name() + ": " + e.getMessage());
            }
        }
        log.accept("Zusammenfassung: %d von %d Entitäten erledigt%s.".formatted(done, entities.size(),
                failed.isEmpty() ? "" : ", FEHLER bei: " + String.join(", ", failed)));
        return all;
    }

    private static Map<String, String> entityValues(Map<String, String> baseValues, Entity entity, boolean byTerms) {
        Map<String, String> values = new HashMap<>(baseValues);
        if (byTerms) {
            values.put(Keys.TERMS, entity.searchTerms() == null || entity.searchTerms().isBlank()
                    ? entity.name() : entity.searchTerms());
        } else {
            values.put(Keys.ENTITY, entity.name());
        }
        return values;
    }
}
