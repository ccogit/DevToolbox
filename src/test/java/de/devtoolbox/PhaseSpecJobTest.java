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
        assertTrue(r.logText().contains("20 Treffer"), r.logText());
        assertTrue(r.logText().contains("30 Dateien"));
        assertTrue(r.logText().contains("Read 4, Process 5, Write 5, Datenzugriff 1, Konfiguration 4, Modell 11"), r.logText());
        assertTrue(r.logText().contains("1 Dateien enthalten den Begriff nur in Kommentaren und sind kein Treffer: "
                + "BeitragsInkassoWriter.java"), r.logText());
        assertEquals(List.of(SKILL, "aufgabe-risiko.md", "code-risiko.md"), r.names());
        assertTrue(r.has(SKILL) && r.has("aufgabe-risiko.md") && r.has("code-risiko.md"));
        assertTrue(r.logText().contains("Zielattribute: 11 von Risiko aus Risiko.json"));
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
        assertTrue(r.logText().contains("20 Dateien"), r.logText());
        String code = r.read("code-risiko.md");
        assertFalse(code.contains("Referenz von"));
        assertFalse(code.contains("/DeckungTO.java"));
    }

    @Test
    void case3_onlyProcess() throws Exception {
        var r = spec(out, Map.of("phases", "PROCESS"));
        assertTrue(r.logText().contains("20 Dateien"), r.logText());
        assertTrue(r.logText().contains("Process 5, Konfiguration 4, Modell 11"), r.logText());
        assertTrue(r.logText().contains("8 Treffer aus nicht gewählten Phasen weggelassen"), r.logText());
        String task = r.read("aufgabe-risiko.md");
        assertTrue(task.contains("**Process**"));
        assertTrue(task.contains("| Nicht betrachten | Read, Write |"), task);
        assertTrue(task.contains("**Phase Process**"));
        assertFalse(task.contains("**Phase Read**"));
        assertFalse(task.contains("**Phase Write**"));
        assertTrue(task.contains("**Zusammenfassung**") && task.contains("**Ablaufübersicht**")
                && task.contains("**Offene-Punkte-Liste (OPL)**"));
        // without Read: the source attributes are listed without numbers, Process has its target list
        assertTrue(task.contains("### Quellattribute (kein Soll in diesem Auftrag)"), task);
        assertFalse(task.contains("### Soll Read"), task);
        assertFalse(task.contains("| R1 |"), task);
        assertTrue(task.contains("### Soll Process – 11 Zeilen (P1–P11)"), task);
        assertTrue(task.contains("(z. B. „## Phase Process“)"), task);
        // A4: the skill only has the section of the selected phase
        String skill = r.read(SKILL);
        assertTrue(skill.contains("### Phase Process"), skill);
        assertFalse(skill.contains("### Phase Read") || skill.contains("### Phase Write"), skill);
        assertFalse(skill.contains("{{"));
        Path other = out.resolve("alle");
        Files.createDirectories(other);
        assertNotEquals(spec(other, Map.of()).read(SKILL), skill);
    }

    @Test
    void case4_packageImportNotIgnored() throws Exception {
        var r = spec(out, Map.of("ignoreDeclarations", "false"));
        assertTrue(r.logText().contains("22 Treffer"), r.logText());
        String code = r.read("code-risiko.md");
        assertTrue(code.contains("Pruefziffer.java"));
        assertFalse(code.lines().anyMatch(l -> l.contains("AltBestandDao.java` | Referenz von")), "AltBestandDao must be a hit");
    }

    @Test
    void case5_withTests() throws Exception {
        var r = spec(out, Map.of("tests", "true"));
        assertTrue(r.logText().contains("21 Treffer"), r.logText());
        String code = r.read("code-risiko.md");
        assertTrue(code.contains("RisikoItemProcessorTest"));
        assertTrue(code.contains("| Test |"), "group Tests expected in table of contents");
        assertTrue(code.contains("## Tests"));
    }

    @Test
    void case6_compact() throws Exception {
        String normal = spec(out.resolve("n"), Map.of("compact", "false")).read("code-risiko.md");
        var r = spec(out.resolve("k"), Map.of());
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
        // C2: the recommendations not used yet, in the fixed order, with the largest unprotected files
        assertTrue(r.logText().contains("Empfehlungen: 1. nur eine Phase wählen; 2. Dateien ausschließen "
                + "(größte nicht geschützte Dateien: "), r.logText());
        assertTrue(r.logText().contains("; 3. Umfang „Nur Klassen mit dem Begriff“."), r.logText());
        assertFalse(r.logText().contains("Tabellen der Entität angeben"), "without DDL the tables have no effect");
        var ddl = spec(out.resolve("ddl"), withDdl("copilotLimit", "1000"));
        assertTrue(ddl.logText().contains("1. nur eine Phase wählen; 2. Tabellen der Entität angeben; 3. Dateien ausschließen"),
                ddl.logText());
        var used = spec(out.resolve("genutzt"), Map.of("copilotLimit", "1000", "phases", "READ", "scope", "hits",
                "excludeFiles", "*Export*"));
        assertTrue(used.logText().contains("Alle Empfehlungen sind schon genutzt."), used.logText());
        var ok = spec(out.resolve("ok"), Map.of("copilotLimit", "10000000"));
        assertFalse(ok.logText().contains("WARNUNG"), ok.logText());
        assertTrue(ok.logText().contains("Geschätzte Größe"), ok.logText());
        // without a value the default applies; 0 switches the check off
        var standard = spec(out.resolve("standard"), Map.of());
        assertTrue(standard.logText().contains("Copilot-Limit 30.000 – ok") || standard.logText().contains("Copilot-Limit 30,000 – ok"),
                standard.logText());
        assertFalse(spec(out.resolve("aus"), Map.of("copilotLimit", "0")).logText().contains("Copilot-Limit"));
    }

    @Test
    void case8_repeatedRunPreservesAllExistingFiles() throws Exception {
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
        var second = spec(out, Map.of());
        assertEquals(folder.resolveSibling(folder.getFileName() + "-2"), second.out());
        assertEquals("alt", Files.readString(folder.resolve("alt.md")));
        assertTrue(Files.exists(folder.resolve("unterordner/bleibt.md")));
        assertTrue(Files.exists(other.resolve("code-vertrag.md")));
        assertTrue(Files.exists(out.resolve("fremd.md")));
        assertTrue(first.files().stream().allMatch(Files::exists));
        assertTrue(second.files().stream().allMatch(Files::exists));
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
        // B1: the term only in a TODO comment is no hit any more; without the setting it is the known miss
        assertFalse(code.contains("BeitragsInkassoWriter.java"));
        assertTrue(spec(out.resolve("mit-kommentaren"), Map.of("ignoreComments", "false")).read("code-risiko.md")
                .contains("BeitragsInkassoWriter.java"));
        // javadoc with ``` must not break the block
        assertEquals(count(code, "===== BEGIN FILE:"), count(code, "===== END FILE:"));
    }

    @Test
    void case10_phaseAssignment() throws Exception {
        String code = spec(out, Map.of()).read("code-risiko.md");
        assertEquals("Read", phaseOf(code, "RisikoDeckungsAnreicherer.java"));
        assertEquals("Process", phaseOf(code, "RisikoSchluesselDao.java"));
        assertEquals("Write", phaseOf(code, "RisikoExportConfig.java"));
        assertEquals("Write", phaseOf(code, "NeuRisikoRepository.java")); // a repository in a write directory
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
        assertTrue(r.logText().contains("Kern: ALT_RISIKO · Neben: ALT_VERTRAG, ALT_OBJEKT, FAKTOREN"), r.logText());
        String task = r.read("aufgabe-risiko.md");
        // A2/A3: the lists are in the task; ALT_RISIKO is the core table, the others are secondary tables
        assertTrue(task.contains("#### Tabelle ALTB.ALT_RISIKO – 8 Zeilen (R1–R8)"), task);
        assertTrue(task.contains("### Nebentabellen (kein Soll)"), task);
        assertTrue(task.contains("#### Tabelle ALTB.ALT_VERTRAG\n"));
        assertTrue(task.contains("#### Tabelle ALTB.ALT_OBJEKT\n"));
        assertTrue(task.contains("#### Tabelle ALTB.FAKTOREN\n"));
        assertTrue(task.indexOf("ALT_RISIKO.GUELTIG_BIS") < task.indexOf("### Nebentabellen"));
        assertFalse(task.contains("ALT_KUNDE"));
        assertTrue(task.contains("- Primärschlüssel: VNR, RISART"));
        assertTrue(task.contains("Fremdschlüssel: (VNR) REFERENCES ALTB.ALT_VERTRAG"));
        assertTrue(task.contains("| R2 | ALT_RISIKO.RISART | CHAR(2) | ja |  | PK | Risikoart |"), task);
        assertTrue(task.contains("FE=Feuer"));
        assertTrue(task.contains("| R7 | ALT_RISIKO.SB_BETRAG |"), "never read column is a target row");
        assertTrue(task.contains("- Gefunden: verwendet in `AltBestandDao.java`"), task);
        String code = r.read("code-risiko.md");
        assertFalse(code.contains("Attributverzeichnis"));
        assertFalse(code.contains("#### Tabelle"));
        // single file works the same
        var single = spec(out.resolve("s"), Map.of("ddl", TESTPROJEKT.resolve("ddl/altbestand.ddl").toString()));
        assertTrue(single.logText().contains("4 Tabellen, 21 Attribute"));
    }

    @Test
    void case12_withoutDdl() throws Exception {
        var r = spec(out, Map.of());
        assertTrue(r.logText().contains("Attributverzeichnis: 36 Attribute aus 8 Datenstrukturen"), r.logText());
        assertTrue(r.logText().contains("Soll Read: 36 Zeilen aus 8 Datenstrukturen · Soll Process: 11 Zeilen"), r.logText());
        String task = r.read("aufgabe-risiko.md");
        String index = task.substring(task.indexOf("### Soll Read"), task.indexOf("### Soll Process"));
        assertTrue(index.startsWith("### Soll Read – 36 Zeilen (R1–R36)"), index);
        assertFalse(index.contains("Klasse Konstanten"));
        assertFalse(index.contains("Klasse Zahlweise"));
        assertFalse(index.contains("### Nebentabellen"), "secondary tables only with DDL");
        assertEquals(8, count(index, "\n#### Klasse "), "8 data structures");
        assertTrue(index.contains("| NeuRisikoEntity.") && index.contains("| Adresse."), index);
        assertTrue(index.contains("| Schlüssel | Typ | Spalte (@Column) |"), "column names from @Column");
        assertTrue(index.contains("| R36 |"));
        assertFalse(r.read("code-risiko.md").contains("Attributverzeichnis"));
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
        assertTrue(r.logText().contains("20 Treffer"), r.logText());
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
        String task = r.read("aufgabe-risiko.md");
        assertTrue(task.contains("### Soll Read"));
        assertFalse(task.contains("Soll Process"));
        assertFalse(task.contains("Für Write gibt es keine Soll-Liste"));
    }

    // --- new cases (TESTFAELLE.md, cases 17 ff.) ---

    private static Map<String, String> withDdl(String... keyValues) {
        Map<String, String> v = new HashMap<>(Map.of("ddl", TESTPROJEKT.resolve("ddl").toString()));
        for (int i = 0; i < keyValues.length; i += 2) {
            v.put(keyValues[i], keyValues[i + 1]);
        }
        return v;
    }

    /** T1: the target lists are in the task, not in the code file. */
    @Test
    void case17_inventoryInTask() throws Exception {
        var r = spec(out, withDdl());
        String task = r.read("aufgabe-risiko.md");
        assertTrue(task.contains("## Soll-Listen"));
        assertTrue(task.contains("### Soll Read – 8 Zeilen (R1–R8)"), task);
        assertTrue(task.contains("| R2 | ALT_RISIKO.RISART |"), task);
        assertTrue(task.contains("### Nebentabellen (kein Soll)"), task);
        assertTrue(task.contains("| ALT_OBJEKT.WOHNFLAECHE | DECIMAL(7, 2) |  | in m²"), task);
        assertTrue(task.contains("### Soll Process – 11 Zeilen (P1–P11)"), task);
        assertTrue(task.contains("| P1 | risikoId | typ: String · pflicht: true |"), task);
        assertTrue(task.contains("Für Write gibt es keine Soll-Liste; siehe Skill, Abschnitt Phase Write."), task);
        assertTrue(task.indexOf("## Soll-Listen") < task.indexOf("## Ergebnis")
                && task.indexOf("## Ergebnis") < task.indexOf("## Vor der Abgabe prüfen"));
        assertFalse(task.contains("Attributverzeichnis"), "no reference to a directory in the code file");
        assertFalse(task.contains("{{"), task);
        assertFalse(r.read(SKILL).contains("{{"));
        String code = r.read("code-risiko.md");
        assertFalse(code.contains("Attributverzeichnis"));
        assertFalse(code.contains("Soll Read"));
        assertTrue(r.logText().contains("Soll Read: 8 Zeilen aus 1 Tabelle · Soll Process: 11 Zeilen"), r.logText());
    }

    /** T2: two core tables, numbers run through, each with its own count. */
    @Test
    void case18_tablesOfEntity() throws Exception {
        var r = spec(out, withDdl("tables", "alt_risiko, ALTB.ALT_OBJEKT"));
        String task = r.read("aufgabe-risiko.md");
        assertTrue(task.contains("### Soll Read – 13 Zeilen (R1–R13)"), task);
        assertTrue(task.contains("#### Tabelle ALTB.ALT_RISIKO – 8 Zeilen (R1–R8)"), task);
        assertTrue(task.contains("#### Tabelle ALTB.ALT_OBJEKT – 5 Zeilen (R9–R13)"), task);
        assertTrue(task.contains("- Primärschlüssel: – (nicht deklariert)"), task);
        assertTrue(task.contains("- Eindeutiger Index: VNR"), task);
        assertTrue(task.contains("| R9 | ALT_OBJEKT.VNR | CHAR(12) | ja |  | UK |"), task);
        assertTrue(task.contains("| R13 | ALT_OBJEKT.WOHNFLAECHE |"), task);
        String secondary = task.substring(task.indexOf("### Nebentabellen"), task.indexOf("### Soll Process"));
        assertTrue(secondary.contains("#### Tabelle ALTB.ALT_VERTRAG") && secondary.contains("#### Tabelle ALTB.FAKTOREN"));
        assertFalse(secondary.contains("ALT_OBJEKT") || secondary.contains("ALT_RISIKO."));
        assertTrue(r.logText().contains("Soll Read: 13 Zeilen aus 2 Tabellen"), r.logText());
        // without DDL the setting has no effect, the log says so
        var noDdl = spec(out.resolve("ohne"), Map.of("tables", "ALT_RISIKO"));
        assertTrue(noDdl.logText().contains("„Tabellen der Entität“ wirkt nur mit DDL"), noDdl.logText());
    }

    /** T3: an unknown table name fails with the list of the tables in the DDL. */
    @Test
    void case19_unknownTable() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> spec(out, withDdl("tables", "GIBT_ES_NICHT")));
        assertTrue(e.getMessage().contains("GIBT_ES_NICHT"), e.getMessage());
        assertTrue(e.getMessage().contains("ALTB.ALT_RISIKO") && e.getMessage().contains("ALTB.ALT_KUNDE"), e.getMessage());
    }

    /** T4: the same column in two core tables gives two keys, one row each. */
    @Test
    void case20_sameColumnInTwoTables() throws Exception {
        var r = spec(out, withDdl("tables", "ALT_RISIKO,ALT_OBJEKT"));
        String task = r.read("aufgabe-risiko.md");
        assertTrue(task.contains("| R1 | ALT_RISIKO.VNR |") && task.contains("| R9 | ALT_OBJEKT.VNR |"), task);
        assertEquals(1, count(task, " | ALT_RISIKO.VNR | "), "one row per key");
        assertEquals(1, count(task, " | ALT_OBJEKT.VNR | "), "one row per key");
    }

    /** T5: the skill contains exactly the sections of the selected phases. */
    @Test
    void case21_skillPerPhases() throws Exception {
        var read = spec(out.resolve("r"), withDdl("phases", "READ"));
        String skill = read.read(SKILL);
        assertTrue(skill.contains("### Phase Read"));
        assertFalse(skill.contains("### Phase Process") || skill.contains("### Phase Write"));
        assertFalse(read.read("aufgabe-risiko.md").contains("Soll Process"));
        var readWrite = spec(out.resolve("rw"), withDdl("phases", "READ,WRITE"));
        String both = readWrite.read(SKILL);
        assertTrue(both.contains("### Phase Read") && both.contains("### Phase Write"));
        assertFalse(both.contains("### Phase Process"));
        assertTrue(both.indexOf("### Phase Read") < both.indexOf("### Phase Write"));
        String task = readWrite.read("aufgabe-risiko.md");
        assertFalse(task.contains("Soll Process"));
        assertTrue(task.contains("Für Write gibt es keine Soll-Liste"));
    }

    /** T6: without a target model file for the entity, "Soll Process" lists the source attributes. */
    @Test
    void case22_processWithoutTargetModelFile() throws Exception {
        Path emptyModel = Files.createDirectories(out.resolve("zielmodell-leer"));
        var r = spec(out.resolve("run"), withDdl("phases", "PROCESS", "targetModel", emptyModel.toString()));
        assertTrue(r.logText().contains("WARNUNG: Keine Zielmodell-Datei"), r.logText());
        String task = r.read("aufgabe-risiko.md");
        assertTrue(task.contains("### Soll Process – 8 Zeilen (P1–P8)"), task);
        assertTrue(task.contains("| P2 | ALT_RISIKO.RISART | CHAR(2) |"), task);
        assertTrue(task.contains("das fehlende Zielmodell ist ein OPL-Punkt"), task);
        assertTrue(task.contains("### Quellattribute (kein Soll in diesem Auftrag)"), task);
    }

    /** No table name contains a search term: all tables mentioned in the code are core tables, with a warning. */
    @Test
    void case23_noTableWithTerm() throws Exception {
        Path ddl = Files.createDirectories(out.resolve("ddl")).resolve("ohne-begriff.ddl");
        Files.writeString(ddl, """
                CREATE TABLE ALTB.ALT_VERTRAG (VNR CHAR(12) NOT NULL, TARIF CHAR(6));
                CREATE TABLE ALTB.FAKTOREN (NAME VARCHAR(30) NOT NULL, WERT DECIMAL(9, 4));
                """);
        var r = spec(out.resolve("run"), Map.of("ddl", ddl.toString()));
        assertTrue(r.read("aufgabe-risiko.md").contains("### Soll Read – 4 Zeilen (R1–R4)"));
        assertTrue(r.logText().contains("WARNUNG: Keine Tabelle enthält einen Suchbegriff – Tabellen der Entität angeben."),
                r.logText());
        assertFalse(r.read("aufgabe-risiko.md").contains("### Nebentabellen"));
    }

    /** Technical table names (as in DB2 legacy schemas): the table is found by its COMMENT ON text. */
    @Test
    void case23b_tableFoundByComment() throws Exception {
        Path ddl = Files.createDirectories(out.resolve("ddl")).resolve("technisch.sql");
        Files.writeString(ddl, """
                CREATE TABLE ALTDB.T91AV5 (VSNR CHAR(12) NOT NULL, RISART CHAR(2));
                COMMENT ON TABLE ALTDB.T91AV5 IS 'Risiko-Kopf';
                CREATE TABLE ALTDB.T9W2VD (VSNR CHAR(12) NOT NULL);
                COMMENT ON TABLE ALTDB.T9W2VD IS 'Daten versichertes Objekt';
                """);
        var r = spec(out.resolve("run"), Map.of("ddl", ddl.toString()));
        String task = r.read("aufgabe-risiko.md");
        assertTrue(task.contains("### Soll Read – 2 Zeilen (R1–R2)"), task);
        assertTrue(task.contains("T91AV5.RISART"), task);
        assertFalse(task.contains("T9W2VD.VSNR"), task);
        assertTrue(task.contains("Bezeichnung/Kommentar enthält Suchbegriff („Risiko-Kopf“)"), task);
    }

    /** DDL given, but no table fits: the log and the task say so (not "keine DDL angegeben") and list the tables. */
    @Test
    void case23c_ddlWithoutMatchingTable() throws Exception {
        Path ddl = Files.createDirectories(out.resolve("ddl")).resolve("fremd.sql");
        Files.writeString(ddl, """
                CREATE TABLE ALTDB.T91EV2 (VSNR CHAR(12) NOT NULL);
                COMMENT ON TABLE ALTDB.T91EV2 IS 'Vorgang';
                """);
        var r = spec(out.resolve("run"), Map.of("ddl", ddl.toString()));
        assertTrue(r.logText().contains("WARNUNG: DDL mit 1 Tabellen angegeben, aber keine passt zu „Risiko“"), r.logText());
        assertTrue(r.logText().contains("Vorhanden: T91EV2 (Vorgang)"), r.logText());
        String task = r.read("aufgabe-risiko.md");
        assertTrue(task.contains("Quelle: Datenstrukturen im Code (DDL mit 1 Tabellen angegeben, aber keine passt"), task);
        assertFalse(task.contains("keine DDL angegeben"), task);
    }

    /** No hit at all: no files, and the log names the reason. */
    @Test
    void case23d_noHitsGivesReason() throws Exception {
        var r = spec(out.resolve("run"), Map.of("terms", "GibtEsNirgendsXyz"));
        assertTrue(r.logText().contains("WARNUNG: Keine Dateien erzeugt – Grund: keine Treffer für „GibtEsNirgendsXyz“"),
                r.logText());
    }

    /** T7: a term only in comment lines is no hit; a hit in the file name still counts. */
    @Test
    void case25_ignoreComments() throws Exception {
        Path base = Files.createDirectories(out.resolve("projekt/src/main/java/de/x"));
        Files.writeString(base.resolve("NurKommentar.java"), """
                package de.x;
                /**
                 * Erwähnt Risiko nur im Javadoc.
                 */
                public class NurKommentar {
                    // Risiko: nur ein Zeilenkommentar
                    /* Risiko im Blockkommentar */
                }
                """);
        Files.writeString(base.resolve("RisikoHinweis.java"), """
                package de.x;
                // Risiko nur im Kommentar, aber im Dateinamen
                public class RisikoHinweis { }
                """);
        Files.writeString(base.resolve("ImCode.java"), """
                package de.x;
                public class ImCode { String typ = "Risiko"; }
                """);
        Files.writeString(base.resolve("abfrage.sql"), "-- Risiko nur im SQL-Kommentar\nSELECT 1 FROM DUAL;\n");
        Path project = out.resolve("projekt");
        Map<String, String> v = Map.of("base", project.toString(), "terms", "Risiko", "phases", "READ");
        var on = spec(out.resolve("an"), v);
        String code = on.read("code-risiko.md");
        assertFalse(code.contains("NurKommentar.java"), code);
        assertFalse(code.contains("abfrage.sql"), code);
        assertTrue(code.contains("RisikoHinweis.java") && code.contains("ImCode.java"), code);
        assertTrue(on.logText().contains("2 Dateien enthalten den Begriff nur in Kommentaren und sind kein Treffer: "
                + "abfrage.sql, NurKommentar.java"), on.logText());
        Map<String, String> off = new HashMap<>(v);
        off.put("ignoreComments", "false");
        String all = spec(out.resolve("aus"), off).read("code-risiko.md");
        assertTrue(all.contains("NurKommentar.java") && all.contains("abfrage.sql") && all.contains("RisikoHinweis.java"));
    }

    /** T8: excluded files are neither hits nor references; the log names their number. */
    @Test
    void case26_excludeFiles() throws Exception {
        var byName = spec(out.resolve("name"), Map.of("excludeFiles", "*Export*"));
        assertFalse(byName.read("code-risiko.md").contains("RisikoExportConfig.java"));
        assertTrue(byName.logText().contains("1 Dateien über „Dateien ausschließen“ ausgeschlossen"), byName.logText());
        var byPath = spec(out.resolve("pfad"), Map.of("excludeFiles", "**/process/**"));
        String code = byPath.read("code-risiko.md");
        assertFalse(code.contains("RisikoItemProcessor.java"), "hit excluded");
        assertFalse(code.contains("BeitragsConverter.java"), "reference excluded");
        assertTrue(code.contains("RisikoSchluesselDao.java"), "other folders stay");
        assertTrue(byPath.logText().contains("4 Dateien über „Dateien ausschließen“ ausgeschlossen"), byPath.logText());
        assertFalse(spec(out.resolve("ohne"), Map.of()).logText().contains("ausschließen“ ausgeschlossen"));
    }

    /** Project for T9: one protected hit with references of every kind, plus hits that may be left out. */
    private Path budgetProject() throws Exception {
        Path project = out.resolve("budget");
        Path java = Files.createDirectories(project.resolve("src/main/java/de/x"));
        Path resources = Files.createDirectories(project.resolve("src/main/resources"));
        Files.writeString(java.resolve("RisikoItemReader.java"), """
                package de.x;
                public class RisikoItemReader {
                    private FrameworkHelfer helfer;
                    private BatchKonfiguration konfiguration;
                    private VertragTO vertrag;
                }
                """);
        Files.writeString(java.resolve("FrameworkHelfer.java"), "package de.x;\n" + padding(20_000)
                + "public class FrameworkHelfer { }\n");
        Files.writeString(java.resolve("BatchKonfiguration.java"), "package de.x;\n" + padding(3_000)
                + "@Configuration\npublic class BatchKonfiguration { }\n");
        Files.writeString(java.resolve("VertragTO.java"), "package de.x;\npublic class VertragTO { private AdresseTO adresse; }\n");
        Files.writeString(java.resolve("AdresseTO.java"), "package de.x;\n" + padding(2_000)
                + "public class AdresseTO { private String ort; }\n");
        Files.writeString(java.resolve("RisikoDto.java"), "package de.x;\n" + padding(1_500)
                + "public class RisikoDto { private String risikoArt; }\n");
        Files.writeString(java.resolve("RisikoDao.java"), "package de.x;\npublic class RisikoDao { }\n");
        Files.writeString(resources.resolve("risiko.properties"), padding(1_000).replace("//", "#") + "risiko.aktiv=true\n");
        return project;
    }

    private static String padding(int chars) {
        String line = "// " + "x".repeat(76) + "\n";
        return line.repeat(chars / line.length());
    }

    /** T9: unprotected files are left out in the fixed order until the code file fits its budget. */
    @Test
    void case27_budgetOmission() throws Exception {
        Path project = budgetProject();
        Map<String, String> v = new HashMap<>(Map.of("base", project.toString(), "terms", "Risiko", "phases", "READ"));
        // without a limit nothing is left out
        v.put("copilotLimit", "0");
        String full = spec(out.resolve("aus"), v).read("code-risiko.md");
        assertFalse(full.contains("Nicht enthaltene Dateien"));
        assertTrue(full.contains("FrameworkHelfer.java"));

        // the largest reference without a clear role is enough
        v.put("copilotLimit", "4000");
        var one = spec(out.resolve("eine"), v);
        String code = one.read("code-risiko.md");
        assertTrue(code.contains("## Nicht enthaltene Dateien"), code);
        assertTrue(code.contains("passten aber nicht ins Budget"), code);
        assertTrue(code.contains("Was nur dort steht, ist ein OPL-Punkt."), code);
        assertTrue(code.contains("| `src/main/java/de/x/FrameworkHelfer.java` | Modell | Budget: Referenz ohne erkennbare Rolle"), code);
        assertTrue(code.indexOf("## Nicht enthaltene Dateien") < code.indexOf("## Inhaltsverzeichnis"));
        assertFalse(code.contains("===== BEGIN FILE: src/main/java/de/x/FrameworkHelfer.java"));
        assertTrue(code.contains("===== BEGIN FILE: src/main/java/de/x/BatchKonfiguration.java"));
        assertEquals(1, count(one.logText(), "- nicht enthalten: "), one.logText());
        assertFalse(one.logText().contains("WARNUNG: Die Code-Datei bleibt"), one.logText());

        // too small: everything unprotected goes, in the fixed order, and a warning follows
        v.put("copilotLimit", "100");
        var all = spec(out.resolve("alle"), v);
        List<String> omitted = all.log().stream().filter(l -> l.startsWith("- nicht enthalten: "))
                .map(l -> l.substring(l.lastIndexOf('/') + 1, l.indexOf(' ', l.lastIndexOf('/')))).toList();
        // files named after the entity are the core and go last
        assertEquals(List.of("FrameworkHelfer.java", "BatchKonfiguration.java", "AdresseTO.java", "RisikoDto.java",
                "risiko.properties"), omitted, all.logText());
        assertTrue(all.logText().contains("Modell-Referenz der Ebene 2"), all.logText());
        assertTrue(all.logText().contains("WARNUNG: Die Code-Datei bleibt"), all.logText());
        String rest = all.read("code-risiko.md");
        for (String kept : List.of("RisikoItemReader.java", "VertragTO.java", "RisikoDao.java")) {
            assertTrue(rest.contains("===== BEGIN FILE: src/main/java/de/x/" + kept), kept + " is protected");
        }
    }

    /**
     * A class that only mentions the entity (facade, aggregate TO) arrives as an excerpt: the members with the term
     * and the members they use. Only the excerpt decides what counts as referenced, and such a class is left out
     * before the files named after the entity – together with the references only it uses.
     */
    @Test
    void case27b_excerptOfClassesMentioningTheEntity() throws Exception {
        Path project = out.resolve("rand");
        Path java = Files.createDirectories(project.resolve("src/main/java/de/x"));
        StringBuilder facade = new StringBuilder("""
                package de.x;
                import de.y.KundeTO;
                import de.y.TarifTO;
                public class VertragFassade {
                    private final Zugriff zugriff = new Zugriff();
                    private int zaehler;

                    public RisikoDto ladeRisiko(String vnr) {
                        return pruefe(new RisikoDto(), new TarifTO());
                    }

                    private RisikoDto pruefe(RisikoDto dto, TarifTO tarif) {
                        zugriff.lies(dto);
                        return dto;
                    }
                """);
        for (int i = 0; i < 12; i++) {
            facade.append("\n    public KundeTO ladeKunde").append(i).append("(String nr) {\n        zaehler++;\n")
                    .append("        return new KundeTO(); // }\n    }\n");
        }
        Files.writeString(java.resolve("VertragFassade.java"), facade.append("}\n"));
        Files.writeString(java.resolve("RisikoItemReader.java"), "package de.x;\npublic class RisikoItemReader { }\n");
        Files.writeString(java.resolve("RisikoDto.java"), "package de.x;\npublic class RisikoDto { private String risikoArt; }\n");
        Files.writeString(java.resolve("Zugriff.java"), "package de.x;\npublic class Zugriff { void lies(Object o) { } }\n");
        Path other = Files.createDirectories(project.resolve("src/main/java/de/y"));
        Files.writeString(other.resolve("KundeTO.java"), "package de.y;\npublic class KundeTO { private String name; }\n");
        Files.writeString(other.resolve("TarifTO.java"), "package de.y;\n" + padding(2_000)
                + "public class TarifTO { private String tarif; }\n");

        Map<String, String> v = new HashMap<>(Map.of("base", project.toString(), "terms", "Risiko", "phases", "READ",
                "copilotLimit", "0"));
        var r = spec(out.resolve("auszug"), v);
        String code = r.read("code-risiko.md");
        assertTrue(r.logText().contains("1 Klassen erwähnen die Entität nur am Rand – als Auszug übernommen"), r.logText());
        assertTrue(code.contains("VertragFassade.java` | 3 Trefferzeilen · Auszug: 3 von 16 Membern"), code);
        assertTrue(code.contains("- „Auszug“: Die Klasse erwähnt den Suchbegriff nur am Rand"), code);
        // the hit member, the helper and the field it uses stay; the unrelated members are marked as left out
        assertTrue(code.contains("public RisikoDto ladeRisiko(String vnr)"), code);
        assertTrue(code.contains("private RisikoDto pruefe(RisikoDto dto, TarifTO tarif)"), code);
        assertTrue(code.contains("private final Zugriff zugriff"), code);
        assertFalse(code.contains("ladeKunde"), code);
        assertFalse(code.contains("private int zaehler"), code);
        assertTrue(code.contains("// … 12 Member ohne Bezug ausgelassen (Zeilen 17–75)"), code);
        // what only the left-out members use is not a reference
        assertFalse(code.contains("KundeTO.java"), code);
        assertTrue(code.contains("===== BEGIN FILE: src/main/java/de/y/TarifTO.java"), code);
        assertTrue(code.contains("===== BEGIN FILE: src/main/java/de/x/Zugriff.java"), code);

        // budget: the class mentioning the entity goes before the one named after it, and takes its references along
        v.put("copilotLimit", "100");
        var small = spec(out.resolve("klein"), v);
        List<String> omitted = small.log().stream().filter(l -> l.startsWith("- nicht enthalten: "))
                .map(l -> l.substring(l.lastIndexOf('/') + 1, l.indexOf(' ', l.lastIndexOf('/')))).toList();
        assertEquals(List.of("Zugriff.java", "VertragFassade.java", "RisikoDto.java"), omitted, small.logText());
        assertTrue(small.logText().contains("1 Referenzen entfallen mit, weil nur nicht enthaltene Dateien sie "
                + "verwenden: TarifTO.java"), small.logText());
        assertFalse(small.read("code-risiko.md").contains("TarifTO.java"));
    }

    /** T10: size report for the three files and the recommendation for many target rows. */
    @Test
    void case28_sizeReport() throws Exception {
        var r = spec(out, Map.of());
        for (String file : List.of(SKILL, "aufgabe-risiko.md", "code-risiko.md")) {
            String line = r.log().stream().filter(l -> l.startsWith("Größe " + file + ": ")).findFirst()
                    .orElseThrow(() -> new AssertionError(file + "\n" + r.logText()));
            assertTrue(line.matches("Größe .+: [\\d.,]+ Zeichen, [\\d.,]+ Zeilen, ca\\. [\\d.,]+ Tokens \\(geschätzt: Zeichen / 4\\)"), line);
        }
        assertTrue(r.logText().contains("Größe gesamt: "), r.logText());
        assertTrue(r.logText().contains("Größte Quelldateien in code-risiko.md: "), r.logText());
        assertEquals(5, count(r.log().stream().filter(l -> l.startsWith("Größte Quelldateien")).findFirst().orElseThrow(),
                " Zeichen)"));
        assertFalse(r.logText().contains("Empfehlung:"), "8 + 11 target rows are fine");

        // more than 60 target rows: one run per phase, or – with one phase – one run per table
        Path ddl = Files.createDirectories(out.resolve("ddl")).resolve("gross.ddl");
        StringBuilder text = new StringBuilder("CREATE TABLE ALTB.ALT_RISIKO (VNR CHAR(12) NOT NULL");
        for (int i = 1; i < 40; i++) {
            text.append(", FELD").append(i).append(" CHAR(1)");
        }
        text.append(");\nCREATE TABLE ALTB.ALT_RISIKO_ZUSATZ (VNR CHAR(12) NOT NULL");
        for (int i = 1; i < 25; i++) {
            text.append(", ZUSATZ").append(i).append(" CHAR(1)");
        }
        Files.writeString(ddl, text.append(");\n"));
        var all = spec(out.resolve("alle"), Map.of("ddl", ddl.toString()));
        assertTrue(all.logText().contains("Soll Read: 65 Zeilen aus 2 Tabellen · Soll Process: 11 Zeilen"), all.logText());
        assertTrue(all.logText().contains("Empfehlung: 76 Soll-Zeilen in einem Lauf (mehr als 60) – einen Lauf je Phase "
                + "starten; die Ergebnisse führt der Job „Spezifikation prüfen“ zusammen."), all.logText());
        var read = spec(out.resolve("read"), Map.of("ddl", ddl.toString(), "phases", "READ"));
        assertTrue(read.logText().contains("Empfehlung: 65 Soll-Zeilen in einem Lauf (mehr als 60) – einen Lauf je "
                + "Tabelle über „Tabellen der Entität“ starten"), read.logText());
    }

    /** T11: start and continuation prompt with the real file names, each at most 1,500 characters. */
    @Test
    void case24_prompts() throws Exception {
        var r = spec(out, Map.of());
        String start = r.log().stream().filter(l -> l.startsWith("Prompt: ")).findFirst().orElseThrow();
        String next = r.log().stream().filter(l -> l.startsWith("Prompt bei UNVOLLSTÄNDIG: ")).findFirst().orElseThrow();
        assertTrue(start.startsWith("Prompt: Bearbeite die Aufgabe in aufgabe-risiko.md nach den Regeln in "
                + SKILL + ". code-risiko.md ist nur Belegmaterial. Lies sie zuerst vollständig ("), start);
        assertTrue(start.contains("Markdown-Datei spezifikation-risiko.md zum Download"), start);
        assertTrue(start.contains("„Status: UNVOLLSTÄNDIG“"), start);
        assertTrue(next.contains("Markdown-Datei spezifikation-risiko-teil2.md zum Download"), next);
        assertFalse(start.contains("<e>") || next.contains("<e>"));
        assertTrue(start.length() - "Prompt: ".length() <= 1500, "start prompt: " + start.length());
        assertTrue(next.length() - "Prompt bei UNVOLLSTÄNDIG: ".length() <= 1500, "continuation: " + next.length());
    }
}
