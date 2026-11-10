package de.devtoolbox;

import de.devtoolbox.core.BatchRunner;
import de.devtoolbox.core.Config;
import de.devtoolbox.core.Job;
import de.devtoolbox.core.JobRegistry;
import de.devtoolbox.core.MigrationProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.devtoolbox.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** TESTFAELLE.md cases 1-16 for the job "phase-spec". */
class PhaseSpecJobTest {

    private static final String SKILL = "skill-phasen-spezifikation.md";

    @TempDir
    Path out;

    @Test
    void jobIsRegistered() {
        Job job = JobRegistry.find("phase-spec").orElseThrow();
        assertEquals("spezifikation", job.fileTag());
        assertTrue(job.params().stream().anyMatch(p -> p.key().equals("base") && p.required()));
        assertTrue(JobRegistry.find("gibt-es-nicht").isEmpty());
    }

    @Test
    void case1_standard() throws Exception {
        var r = spec(out, Map.of());
        assertTrue(r.logText().contains("21 Treffer"), r.logText());
        assertTrue(r.logText().contains("31 Dateien"));
        assertTrue(r.logText().contains("Read 5, Process 5, Write 5, Datenzugriff 1, Konfiguration 4, Modell 11"), r.logText());
        assertEquals(List.of(SKILL, "aufgabe-risiko.md", "code-risiko.md"), r.names());
        assertTrue(r.has(SKILL) && r.has("aufgabe-risiko.md") && r.has("code-risiko.md"));
        assertTrue(r.logText().contains("Zielattribute: 11 aus Risiko.json"));
    }

    @Test
    void case1_skillContainsAllPhases() throws Exception {
        var r = spec(out, Map.of());
        String skill = r.read(SKILL);
        assertTrue(skill.contains("Phase Read") && skill.contains("Phase Process") && skill.contains("Phase Write"));
    }

    @Test
    void case2_onlyHits() throws Exception {
        var r = spec(out, Map.of("scope", "hits"));
        assertTrue(r.logText().contains("21 Dateien"), r.logText());
        String code = r.read("code-risiko.md");
        assertFalse(code.contains("Referenz von"));
        assertFalse(code.contains("/DeckungTO.java"));
    }

    @Test
    void case3_onlyProcess() throws Exception {
        var r = spec(out, Map.of("phases", "PROCESS"));
        assertTrue(r.logText().contains("20 Dateien"), r.logText());
        assertTrue(r.logText().contains("Process 5, Konfiguration 4, Modell 11"), r.logText());
        assertTrue(r.logText().contains("9 Treffer aus nicht gewählten Phasen weggelassen"), r.logText());
        String task = r.read("aufgabe-risiko.md");
        assertTrue(task.contains("**Process**"));
        assertTrue(task.contains("| Nicht betrachten | Read, Write |"), task);
        assertTrue(task.contains("**Phase Process**"));
        assertFalse(task.contains("**Phase Read**"));
        assertFalse(task.contains("**Phase Write**"));
        assertTrue(task.contains("**Zusammenfassung**") && task.contains("**Ablaufübersicht**")
                && task.contains("**Offene-Punkte-Liste (OPL)**"));
        String skill = r.read(SKILL);
        assertTrue(skill.contains("Phase Read") && skill.contains("Phase Write"));
        // skill is independent of the selection
        Path other = out.resolve("alle");
        Files.createDirectories(other);
        assertEquals(spec(other, Map.of()).read(SKILL), skill);
    }

    @Test
    void case4_packageImportNotIgnored() throws Exception {
        var r = spec(out, Map.of("ignoreDeclarations", "false"));
        assertTrue(r.logText().contains("23 Treffer"), r.logText());
        String code = r.read("code-risiko.md");
        assertTrue(code.contains("Pruefziffer.java"));
        assertFalse(code.lines().anyMatch(l -> l.contains("AltBestandDao.java` | Referenz von")), "AltBestandDao must be a hit");
    }

    @Test
    void case5_withTests() throws Exception {
        var r = spec(out, Map.of("tests", "true"));
        assertTrue(r.logText().contains("22 Treffer"), r.logText());
        String code = r.read("code-risiko.md");
        assertTrue(code.contains("RisikoItemProcessorTest"));
        assertTrue(code.contains("| Test |"), "group Tests expected in table of contents");
        assertTrue(code.contains("## Tests"));
    }

    @Test
    void case6_compact() throws Exception {
        String normal = spec(out.resolve("n"), Map.of()).read("code-risiko.md");
        var r = spec(out.resolve("k"), Map.of("compact", "true"));
        String code = r.read("code-risiko.md");
        assertTrue(normal.contains("Lizenzkopf"));
        assertTrue(normal.contains("\nimport de.vers"));
        assertFalse(code.contains("Lizenzkopf"));
        assertFalse(code.lines().anyMatch(l -> l.startsWith("import ")));
        assertTrue(code.length() < normal.length());
    }

    @Test
    void case7_singleFileEvenWithLargeCode() throws Exception {
        var r = spec(out, Map.of("maxTokens", "3000"));
        assertEquals(List.of(SKILL, "aufgabe-risiko.md", "code-risiko.md"), r.names());
        String code = r.read("code-risiko.md");
        assertFalse(code.contains("| Teil |"));
        assertEquals(1, count(code, "## Inhaltsverzeichnis"));
        assertFalse(r.logText().contains("WARNUNG"), r.logText());
    }

    @Test
    void case7b_copilotLimitWarning() throws Exception {
        var r = spec(out, Map.of("copilotLimit", "1000"));
        assertTrue(r.logText().contains("WARNUNG: Geschätzt ca."), r.logText());
        assertTrue(r.logText().contains("Copilot-Limit von 1.000") || r.logText().contains("Copilot-Limit von 1,000"), r.logText());
        assertTrue(r.logText().contains("Kompakt"), r.logText());
        var ok = spec(out.resolve("ok"), Map.of("copilotLimit", "10000000"));
        assertFalse(ok.logText().contains("WARNUNG"), ok.logText());
        assertTrue(ok.logText().contains("Geschätzte Größe"), ok.logText());
        assertFalse(spec(out.resolve("leer"), Map.of()).logText().contains("Copilot-Limit"));
    }

    @Test
    void case8_runFolderIsReusedAndOnlyItsFilesAreDeleted() throws Exception {
        var first = spec(out, Map.of());
        Path folder = first.out();
        assertEquals(out.resolve("devtoolbox").resolve("ohne-projekt-spezifikation-risiko"), folder);
        assertTrue(first.files().stream().allMatch(p -> p.getParent().equals(folder)));
        Files.writeString(folder.resolve("alt.md"), "alt");
        Files.createDirectories(folder.resolve("unterordner"));
        Files.writeString(folder.resolve("unterordner/bleibt.md"), "x");
        Path other = out.resolve("devtoolbox/ohne-projekt-spezifikation-vertrag");
        Files.createDirectories(other);
        Files.writeString(other.resolve("code-vertrag.md"), "x");
        Files.writeString(out.resolve("fremd.md"), "x");
        var r = spec(out, Map.of());
        assertEquals(folder, r.out());
        assertFalse(Files.exists(folder.resolve("alt.md")));
        assertTrue(Files.exists(folder.resolve("unterordner/bleibt.md")));
        assertTrue(Files.exists(other.resolve("code-vertrag.md")));
        assertTrue(Files.exists(out.resolve("fremd.md")));
    }

    @Test
    void runFolderContainsProjectId() throws Exception {
        MigrationProject p = MigrationProject.find("testziel").orElseThrow();
        Map<String, String> v = new HashMap<>(Map.of("terms", "Risiko, Risk", "phases", "READ,PROCESS,WRITE",
                MigrationProject.KEY, "testziel"));
        v.putAll(p.values());
        var r = run("phase-spec", v, out);
        assertEquals("testziel-spezifikation-risiko", r.out().getFileName().toString());
    }

    @Test
    void batchRunnerContinuesAfterError() throws Exception {
        Job job = JobRegistry.find("phase-spec").orElseThrow();
        List<String> log = new java.util.ArrayList<>();
        Map<String, String> base = specValues(out);
        base.remove("terms");
        var files = BatchRunner.run(job, base, List.of(
                new BatchRunner.Entity("Risiko", "Risiko, Risk"),
                new BatchRunner.Entity("Gibtesnicht"),
                new BatchRunner.Entity("Risiko")), log::add);
        String text = String.join("\n", log);
        assertEquals(6, files.size(), text);
        assertTrue(text.contains("Zusammenfassung: 3 von 3"), text);

        // an error (missing target model for Process) is reported per entity
        Map<String, String> bad = specValues(out);
        bad.remove("terms");
        bad.remove("targetModel");
        List<String> log2 = new java.util.ArrayList<>();
        var none = BatchRunner.run(job, bad, List.of(new BatchRunner.Entity("Risiko")), log2::add);
        String text2 = String.join("\n", log2);
        assertTrue(none.isEmpty());
        assertTrue(text2.contains("FEHLER bei Entität Risiko"), text2);
        assertTrue(text2.contains("Zusammenfassung: 0 von 1 Entitäten erledigt, FEHLER bei: Risiko"), text2);
    }

    @Test
    void case9_contentRules() throws Exception {
        String code = spec(out, Map.of()).read("code-risiko.md");
        assertFalse(code.contains("RisikoGeneriert"));
        assertFalse(code.contains("/BetragFormatierer.java"));
        assertFalse(code.toLowerCase().contains("kunde/"));
        assertFalse(code.contains("ALT_KUNDE"));
        assertFalse(code.contains("﻿"), "no BOM char in output");
        assertTrue(code.contains("Gebäude oder Hausrat"), "Windows-1252 decoded");
        assertFalse(code.contains("�"));
        assertTrue(code.lines().anyMatch(l -> l.startsWith("-- Datei mit BOM")));
        // transitive TO chain
        assertTrue(code.contains("DeckungTO.java") && code.contains("SelbstbeteiligungTO.java"));
        assertTrue(code.contains("Referenz von DeckungTO.java"));
        // CRLF files come out with LF
        assertFalse(code.contains("\r"));
        // expected miss: textual search finds the TODO comment
        assertTrue(code.contains("BeitragsInkassoWriter.java"));
        // javadoc with ``` must not break the block
        assertEquals(count(code, "===== BEGIN FILE:"), count(code, "===== END FILE:"));
    }

    @Test
    void case10_phaseAssignment() throws Exception {
        String code = spec(out, Map.of()).read("code-risiko.md");
        assertEquals("Read", phaseOf(code, "RisikoDeckungsAnreicherer.java"));
        assertEquals("Process", phaseOf(code, "RisikoSchluesselDao.java"));
        assertEquals("Write", phaseOf(code, "RisikoExportConfig.java"));
        assertEquals("Read", phaseOf(code, "NeuRisikoRepository.java"));
        assertEquals("Datenzugriff", phaseOf(code, "AltBestandDao.java"));
    }

    private static String phaseOf(String code, String file) {
        return code.lines().filter(l -> l.matches("\\| \\d+ \\| .* \\| `.*/" + file + "` \\|.*"))
                .map(l -> l.split("\\|")[2].strip()).findFirst().orElseThrow(() -> new AssertionError(file));
    }

    @Test
    void case11_ddlDirectory() throws Exception {
        var r = spec(out, Map.of("ddl", TESTPROJEKT.resolve("ddl").toString()));
        assertTrue(r.logText().contains("Attributverzeichnis: 4 Tabellen, 21 Attribute aus DDL"), r.logText());
        String code = r.read("code-risiko.md");
        assertTrue(code.contains("### Tabelle ALTB.ALT_RISIKO"));
        assertTrue(code.contains("### Tabelle ALTB.ALT_VERTRAG"));
        assertTrue(code.contains("### Tabelle ALTB.ALT_OBJEKT"));
        assertTrue(code.contains("### Tabelle ALTB.FAKTOREN"));
        assertFalse(code.contains("ALT_KUNDE"));
        assertTrue(code.contains("- Primärschlüssel: VNR, RISART"));
        assertTrue(code.contains("Fremdschlüssel: (VNR) REFERENCES ALTB.ALT_VERTRAG"));
        assertTrue(code.contains("| RISART | CHAR(2) | ja |  | PK | Risikoart |"));
        assertTrue(code.contains("FE=Feuer"));
        assertTrue(code.contains("- Primärschlüssel: – (nicht deklariert)"));
        assertTrue(code.contains("- Eindeutiger Index: VNR"));
        assertTrue(code.contains("| VNR | CHAR(12) | ja |  | UK |"));
        assertTrue(code.contains("SB_BETRAG"));
        // single file works the same
        var single = spec(out.resolve("s"), Map.of("ddl", TESTPROJEKT.resolve("ddl/altbestand.ddl").toString()));
        assertTrue(single.logText().contains("4 Tabellen, 21 Attribute"));
    }

    @Test
    void case12_withoutDdl() throws Exception {
        var r = spec(out, Map.of());
        assertTrue(r.logText().contains("Attributverzeichnis: 36 Attribute aus 8 Datenstrukturen"), r.logText());
        String code = r.read("code-risiko.md");
        int start = code.indexOf("## Attributverzeichnis");
        String index = code.substring(start, code.indexOf("## Inhaltsverzeichnis"));
        assertFalse(index.contains("### Konstanten"));
        assertFalse(index.contains("### Zahlweise"));
        assertEquals(8, count(index, "\n### ") - 1, "8 data structures + Zielattribute heading");
        assertTrue(index.contains("NeuRisikoEntity") && index.contains("Adresse"));
    }

    @Test
    void case13_missingDdl() {
        Exception e = assertThrows(Exception.class,
                () -> spec(out, Map.of("ddl", out.resolve("gibt-es-nicht").toString())));
        assertTrue(e.getMessage().contains("DDL nicht gefunden"), e.getMessage());
    }

    @Test
    void case14_project() throws Exception {
        MigrationProject p = MigrationProject.find("testziel").orElseThrow();
        Map<String, String> v = new HashMap<>(Map.of("terms", "Risiko, Risk", "phases", "READ,PROCESS,WRITE",
                MigrationProject.KEY, "testziel", "output", out.toString()));
        v.putAll(p.values());
        var r = run("phase-spec", v, out);
        assertTrue(r.logText().contains("21 Treffer"));
        assertTrue(r.logText().contains("4 Tabellen, 21 Attribute"), r.logText());
        String task = r.read("aufgabe-risiko.md");
        assertTrue(task.contains("TESTZIEL"), task);
        assertTrue(task.contains("TEST"), task);
        assertTrue(task.contains("testprojekt"));
        assertTrue(r.logText().contains("Zielattribute: 11"));
    }

    @Test
    void case15_projectWithoutReference() {
        MigrationProject vgb = MigrationProject.find("vgb").orElseThrow();
        assertNull(vgb.reference());
        Job job = JobRegistry.find("phase-spec").orElseThrow();
        Config config = Config.of(job.params(), Map.of("terms", "Risiko", MigrationProject.KEY, "vgb"), vgb.values());
        assertTrue(config.missing(job.params()).contains("Referenzprojekt (Code)"));
        assertThrows(IllegalArgumentException.class, () -> job.run(config, s -> { }));
    }

    @Test
    void case16_detail() throws Exception {
        String compact = spec(out.resolve("c"), Map.of("detail", "compact")).read("aufgabe-risiko.md");
        String detailed = spec(out.resolve("d"), Map.of()).read("aufgabe-risiko.md");
        assertTrue(compact.contains("kompakt") && compact.contains("höchstens 5 Sätze"));
        assertTrue(detailed.contains("ausführlich") && detailed.contains("einzelne Schritte"));
        assertFalse(detailed.contains("höchstens 5 Sätze"));
    }

    @Test
    void taskContents() throws Exception {
        String task = spec(out, Map.of("context", "Zielprodukt X")).read("aufgabe-risiko.md");
        assertTrue(task.contains("spezifikation-risiko.md"));
        assertTrue(task.contains("Zielprodukt X"));
        assertTrue(task.contains("Keine weiteren Abschnitte"));
        assertTrue(task.contains("Offene-Punkte-Liste (OPL)"));
        assertTrue(task.contains("`code-risiko.md`"));
        assertTrue(task.contains("**Phase Read**") && task.contains("**Phase Process**") && task.contains("**Phase Write**"));
        assertFalse(task.contains("{{"));
    }

    @Test
    void errors() {
        Map<String, String> v = specValues(out);
        v.put("phases", "PROCESS");
        v.remove("targetModel");
        Exception e = assertThrows(IllegalArgumentException.class, () -> run("phase-spec", v, out));
        assertTrue(e.getMessage().contains("Zielmodell"));
        Map<String, String> w = specValues(out);
        w.put("base", out.resolve("nix").toString());
        assertThrows(IllegalArgumentException.class, () -> run("phase-spec", w, out));
    }

    @Test
    void readOnlyNeedsNoTargetModel() throws Exception {
        Map<String, String> v = specValues(out);
        v.put("phases", "READ");
        v.remove("targetModel");
        var r = run("phase-spec", v, out);
        assertFalse(r.read("code-risiko.md").contains("Zielattribute (Zielmodell)"));
    }
}
