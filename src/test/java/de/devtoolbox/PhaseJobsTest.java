package de.devtoolbox;

import de.devtoolbox.core.Job;
import de.devtoolbox.core.JobRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.devtoolbox.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** The three jobs "spec-read", "spec-process" and "spec-write": one specification job per phase. */
class PhaseJobsTest {

    private static final String SKILL = "skill-phasen-spezifikation.md";

    @TempDir
    Path out;

    private TestSupport.Result runJob(String id) throws Exception {
        Map<String, String> v = new HashMap<>(specValues(out));
        return run(id, v, out.resolve(id));
    }

    /** Temporary test jobs A, B, C: the same job with growing depth of the result. */
    @Test
    void testJobsGrowInDepth() throws Exception {
        TestSupport.Result a = runJob("phase-spec-test-a");
        TestSupport.Result b = runJob("phase-spec-test-b");
        TestSupport.Result c = runJob("phase-spec-test-c");

        // A: what the regular jobs produce with "ausführlich"
        assertFalse(a.read(SKILL).contains("## Regeldetails"));
        assertTrue(a.read(SKILL).contains("Werte ausschreiben"));
        assertTrue(a.read("aufgabe-risiko.md").contains("Keine weiteren Abschnitte oder Tabellen: Jede Information"));
        assertTrue(a.read("aufgabe-risiko.md").contains("spezifikation-risiko-test-a.md"));

        assertTrue(b.read(SKILL).contains("## Regeldetails") && !b.read(SKILL).contains("## Abdeckung"));
        assertTrue(b.read("aufgabe-risiko.md").contains("**Regeldetails Process**"));
        assertFalse(b.read("aufgabe-risiko.md").contains("**Abdeckung Process**"));
        assertTrue(b.read("aufgabe-risiko.md").contains("spezifikation-risiko-test-b.md"));

        assertTrue(c.read(SKILL).contains("## Regeldetails") && c.read(SKILL).contains("## Abdeckung"));
        assertTrue(c.read("aufgabe-risiko.md").contains("**Abdeckung Process**"));
        assertTrue(c.read("aufgabe-risiko.md").contains("spezifikation-risiko-test-c.md"));

        for (TestSupport.Result r : List.of(a, b, c)) {
            assertFalse(r.read(SKILL).contains("{{"), "unfilled placeholder in the skill");
            assertFalse(r.read("aufgabe-risiko.md").contains("{{"), "unfilled placeholder in the task");
            assertTrue(r.read("aufgabe-risiko.md").contains("ausführlich"));
        }
        // the detail level is fixed for the test jobs
        Job job = JobRegistry.find("phase-spec-test-b").orElseThrow();
        assertTrue(job.params().stream().noneMatch(p -> p.key().equals("detail")));
        assertTrue(job.name().startsWith("TEST B"));
    }

    @Test
    void registeredInTheUiAndCombinedJobOnlyByCli() {
        List<String> ids = JobRegistry.all().stream().map(Job::id).toList();
        assertTrue(ids.containsAll(List.of("spec-read", "spec-process", "spec-write")), ids.toString());
        assertFalse(ids.contains("phase-spec"));
        assertTrue(JobRegistry.find("phase-spec").isPresent());
        assertEquals("Spezifikation Read (Einlesen)", JobRegistry.find("spec-read").orElseThrow().name());
        assertEquals("Spezifikation Process (Mapping)", JobRegistry.find("spec-process").orElseThrow().name());
        assertEquals("Spezifikation Write (Schreiben)", JobRegistry.find("spec-write").orElseThrow().name());
    }

    @Test
    void formHasNoPhasesFieldAndTargetModelOnlyForProcess() {
        for (String id : List.of("spec-read", "spec-process", "spec-write")) {
            List<String> keys = JobRegistry.find(id).orElseThrow().params().stream().map(p -> p.key()).toList();
            assertFalse(keys.contains("phases"), id);
            assertEquals(id.equals("spec-process"), keys.contains("targetModel"), id);
            assertTrue(keys.contains("context"), id);
        }
        assertTrue(JobRegistry.find("phase-spec").orElseThrow().params().stream().anyMatch(p -> p.key().equals("phases")));
    }

    @Test
    void readJob() throws Exception {
        var r = runJob("spec-read");
        assertEquals(List.of(SKILL, "aufgabe-risiko.md", "code-risiko.md"), r.names());
        assertTrue(r.out().getFileName().toString().contains("spezifikation-read-risiko"), r.out().toString());
        String skill = r.read(SKILL);
        assertTrue(skill.contains("Phase Read"));
        assertFalse(skill.contains("Phase Process") || skill.contains("Phase Write"));
        String task = r.read("aufgabe-risiko.md");
        assertTrue(task.contains("Soll Read"), task);
        assertFalse(task.contains("Soll Process"), task);
        assertTrue(r.logText().contains("Read 5") && !r.logText().contains("Process 5"), r.logText());
    }

    @Test
    void processJob() throws Exception {
        var r = runJob("spec-process");
        assertEquals(List.of(SKILL, "aufgabe-risiko.md", "code-risiko.md"), r.names());
        assertTrue(r.out().getFileName().toString().contains("spezifikation-process-risiko"), r.out().toString());
        String skill = r.read(SKILL);
        assertTrue(skill.contains("Phase Process"));
        assertFalse(skill.contains("Phase Read") || skill.contains("Phase Write"));
        String task = r.read("aufgabe-risiko.md");
        assertTrue(task.contains("Soll Process"), task);
        assertFalse(task.contains("Soll Read"), task);
        assertTrue(r.logText().contains("Zielattribute: 11"), r.logText());
    }

    @Test
    void writeJob() throws Exception {
        var r = runJob("spec-write");
        assertEquals(List.of(SKILL, "aufgabe-risiko.md", "code-risiko.md"), r.names());
        assertTrue(r.out().getFileName().toString().contains("spezifikation-write-risiko"), r.out().toString());
        String skill = r.read(SKILL);
        assertTrue(skill.contains("Phase Write"));
        assertFalse(skill.contains("Phase Read") || skill.contains("Phase Process"));
        String task = r.read("aufgabe-risiko.md");
        assertFalse(task.contains("Soll Read") || task.contains("Soll Process"), task);
        assertTrue(task.contains("**Phase Write**") && !task.contains("**Phase Read**"), task);
    }

    @Test
    void fixedPhaseIgnoresPhasesValueAndNeverRecommendsRunPerPhase() throws Exception {
        Map<String, String> v = new HashMap<>(specValues(out));
        v.put("phases", "READ,PROCESS,WRITE");
        var r = run("spec-read", v, out.resolve("x"));
        assertFalse(r.read(SKILL).contains("Phase Process"));
        assertFalse(r.logText().contains("einen Lauf je Phase"), r.logText());
    }
}
