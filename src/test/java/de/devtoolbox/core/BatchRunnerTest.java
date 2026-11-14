package de.devtoolbox.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class BatchRunnerTest {

    @Test
    void continuesAfterErrorsAndUsesPerEntitySearchTerms() {
        List<String> logs = new ArrayList<>();
        List<String> received = new ArrayList<>();
        Job job = job(true, (config, log) -> {
            received.add(config.text(Keys.TERMS));
            if (config.text(Keys.TERMS).equals("Fehler")) {
                throw new IllegalArgumentException("Testfehler");
            }
            return List.of(Path.of(config.text(Keys.TERMS) + ".md"));
        });
        Map<String, String> base = Map.of(Keys.TERMS, "Original", Keys.CONTEXT, "behalten");

        List<Path> result = BatchRunner.run(job, base,
                List.of(new BatchRunner.Entity("Risiko", "Wagnis"),
                        new BatchRunner.Entity("Fehler"),
                        new BatchRunner.Entity("Vertrag")), logs::add);

        assertEquals(List.of(Path.of("Wagnis.md"), Path.of("Vertrag.md")), result);
        assertEquals(List.of("Wagnis", "Fehler", "Vertrag"), received);
        assertTrue(logs.stream().anyMatch(line -> line.contains("Entität 3 von 3: Vertrag")));
        assertTrue(logs.stream().anyMatch(line -> line.contains("2 von 3 Entitäten erledigt")));
        assertEquals("Original", base.get(Keys.TERMS), "Base configuration remains unchanged");
    }

    @Test
    void jobsWithoutTermsUseEntityKey() {
        List<String> received = new ArrayList<>();
        Job job = job(false, (config, log) -> {
            received.add(config.text(Keys.ENTITY));
            return List.of();
        });
        BatchRunner.run(job, Map.of(Keys.ENTITY, "Alt"), List.of(new BatchRunner.Entity("Neu")), _ -> {});
        assertEquals(List.of("Neu"), received);
    }

    private static Job job(boolean byTerms, Runner runner) {
        return new Job() {
            @Override public String id() { return "test"; }
            @Override public String name() { return "Test"; }
            @Override public JobInfo info() { return new JobInfo("", "", "", ""); }
            @Override public List<Param> params() {
                return List.of(Param.text(byTerms ? Keys.TERMS : Keys.ENTITY, "Entität", "", true, ""));
            }
            @Override public List<Path> run(Config config, Consumer<String> log) throws Exception {
                return runner.run(config, log);
            }
        };
    }

    @FunctionalInterface
    private interface Runner {
        List<Path> run(Config config, Consumer<String> log) throws Exception;
    }
}
