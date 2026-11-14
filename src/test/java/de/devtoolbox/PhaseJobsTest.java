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

/** The specification job of the UI with its variants, and the jobs "spec-read", "spec-process" and "spec-write" fixed to one phase. */
class PhaseJobsTest {

    private static final String SKILL = "skill-phasen-spezifikation.md";

    @TempDir
    Path out;

    private TestSupport.Result runJob(String id) throws Exception {
        Map<String, String> v = new HashMap<>(specValues(out));
        return run(id, v, out.resolve(id));
    }

    /** The UI offers one specification job with the phases as a field; the jobs per phase remain for the CLI. */
    @Test
    void oneJobInTheUiAndPhaseJobsOnlyByCli() {
        List<String> ids = JobRegistry.all().stream().map(Job::id).toList();
        assertTrue(ids.contains("phase-spec"), ids.toString());
        assertTrue(ids.stream().noneMatch(id -> id.startsWith("spec-read") || id.startsWith("spec-process")
                || id.startsWith("spec-write") || id.contains("test")), ids.toString());
        Job job = JobRegistry.find("phase-spec").orElseThrow();
        assertTrue(job.params().stream().anyMatch(p -> p.key().equals("phases")));
        assertEquals(List.of("tables", "logic", "stages", "chat"), job.params().stream()
                .filter(p -> p.key().equals("variant")).findFirst().orElseThrow()
                .options().stream().map(o -> o.value()).toList());
        assertEquals("Spezifikation Read (Einlesen)", JobRegistry.find("spec-read").orElseThrow().name());
        assertEquals("Spezifikation Process (Mapping)", JobRegistry.find("spec-process").orElseThrow().name());
        assertEquals("Spezifikation Write (Schreiben)", JobRegistry.find("spec-write").orElseThrow().name());
    }

    private TestSupport.Result variant(String variant) throws Exception {
        Map<String, String> v = new HashMap<>(specValues(out));
        v.put("variant", variant);
        v.put("phases", "PROCESS");
        return run("phase-spec", v, out.resolve(variant));
    }

    /** Variant A: no Logikprotokoll, no "Soll Logik"; the file must hold the complete text. */
    @Test
    void variantTables() throws Exception {
        var r = variant("tables");
        assertFalse(r.read(SKILL).contains("## Logikprotokoll"));
        String task = r.read("aufgabe-risiko.md");
        assertFalse(task.contains("Soll Logik") || task.contains("## Etappen"), task);
        assertTrue(task.contains("`spezifikation-risiko.md`"));
        assertTrue(task.contains("21 Quelldateien"), task);
        assertTrue(r.logText().contains("Lies sie zuerst vollständig (21 Dateien"), r.logText());
    }

    /** Variant D: the methods counted from the code are a target list, the Logikprotokoll comes before the tables. */
    @Test
    void variantLogic() throws Exception {
        var r = variant("logic");
        assertTrue(r.read(SKILL).contains("## Logikprotokoll") && r.read(SKILL).contains("| Nr | Bedingung | Ergebnis | Original | Betrifft |"));
        String task = r.read("aufgabe-risiko.md");
        assertTrue(task.contains("### Soll Logik – "), task);
        assertTrue(task.contains("`RisikoartMapper.mappe`") && task.contains("\"FE\""), task);
        assertTrue(task.contains("### Soll Konstanten"), task);
        assertTrue(task.indexOf("**Logikprotokoll**") < task.indexOf("**Phase Process**"), task);
        assertTrue(task.contains("`spezifikation-risiko-d.md`") && !task.contains("## Etappen"), task);
        assertTrue(task.contains("6. Jede Nr der Soll Logik hat genau einen Unterabschnitt"), task);
        assertFalse(task.contains("{{") || r.read(SKILL).contains("{{"));
        String start = r.log().stream().filter(l -> l.startsWith("Prompt: ")).findFirst().orElseThrow();
        assertTrue(start.contains("1. Logikprotokoll") && start.contains("spezifikation-risiko-d.md"), start);
        assertTrue(start.length() <= 1500, "start prompt: " + start.length());
    }

    /** Variant E: one prompt per group of methods, the tables in the last one. */
    @Test
    void variantStages() throws Exception {
        var r = variant("stages");
        String task = r.read("aufgabe-risiko.md");
        assertTrue(task.contains("## Etappen") && task.contains("`spezifikation-risiko-e-logik-1.md`"), task);
        List<String> prompts = r.log().stream().filter(l -> l.startsWith("Prompt")).toList();
        assertTrue(prompts.getFirst().startsWith("Prompt: ") && prompts.getFirst().contains("nur Etappe 1"), prompts.getFirst());
        assertTrue(prompts.stream().anyMatch(l -> l.contains("(Abschluss)") && l.contains("spezifikation-risiko-e.md")), prompts.toString());
        long stages = prompts.stream().filter(l -> l.contains("Logikprotokoll für L")).count();
        assertEquals(stages + 2, prompts.size(), prompts.toString()); // + Abschluss + Abbruch
        assertTrue(task.contains("in " + (stages + 1) + " Antworten"), task);
        prompts.forEach(l -> assertTrue(l.length() <= 1500, l));
    }

    /** Variant F: the answer goes into the chat, continued with "WEITER ab". */
    @Test
    void variantChat() throws Exception {
        var r = variant("chat");
        String task = r.read("aufgabe-risiko.md");
        assertTrue(task.contains("direkt im Chat als Markdown") && !task.contains("zum Download"), task);
        assertTrue(r.logText().contains("Prompt für jede Fortsetzung: Weiter ab der genannten Nr"), r.logText());
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
        assertTrue(r.logText().contains("Read 3") &&!r.logText().contains("Process 5"), r.logText());
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
