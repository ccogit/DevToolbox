package de.devtoolbox;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Job;
import de.devtoolbox.core.JobRegistry;
import de.devtoolbox.core.Texts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static de.devtoolbox.TestSupport.count;
import static org.junit.jupiter.api.Assertions.*;

/** TESTFAELLE.md cases T12 to T16 (job "spec-check"); all data is built in a temp dir. */
class SpecCheckJobTest {

    private static final String OK_EVIDENCE = "AltRisikoRowMapper.mapRow";

    @TempDir
    Path dir;

    // ---------------------------------------------------------------- fixtures

    private static String key(int i) {
        return "ALT_RISIKO.C" + i;
    }

    /** A task in the format of A2 with {@code reads} lines R1.. and {@code processes} lines P1.. */
    private static String task(int reads, int processes) {
        StringBuilder sb = new StringBuilder("# Aufgabe: Spezifikation „Risiko“\n\n## Soll-Listen\n\n");
        if (reads > 0) {
            sb.append("### Soll Read – ").append(reads).append(" Zeilen (R1–R").append(reads).append(")\n\n");
            sb.append("#### Tabelle ALTB.ALT_RISIKO – ").append(reads).append(" Zeilen (R1–R").append(reads)
                    .append(")\n\nRisiken je Vertrag\n\n- Primärschlüssel: VNR\n\n");
            sb.append("| Nr | Schlüssel | Typ | Pflicht | Default | Schlüsselart | Bezeichnung | Kommentar |\n")
                    .append("|---|---|---|---|---|---|---|---|\n");
            for (int i = 1; i <= reads; i++) {
                sb.append("| R").append(i).append(" | ").append(key(i)).append(" | CHAR(2) | ja |  |  |  |  |\n");
            }
            sb.append("\n### Nebentabellen (kein Soll)\n\n#### Tabelle ALTB.ALT_OBJEKT\n\n")
                    .append("| Spalte | Typ | Bezeichnung | Kommentar |\n|---|---|---|---|\n")
                    .append("| ALT_OBJEKT.WOHNFLAECHE | DECIMAL(7, 2) |  |  |\n\n");
        }
        if (processes > 0) {
            sb.append("### Soll Process – ").append(processes).append(" Zeilen (P1–P").append(processes)
                    .append(")\n\nQuelle: Zielmodell, Entität **Risiko** aus `Risiko.json`.\n\n")
                    .append("| Nr | Schlüssel | Angaben im Zielmodell |\n|---|---|---|\n");
            for (int i = 1; i <= processes; i++) {
                sb.append("| P").append(i).append(" | ziel").append(i).append(" | typ: String |\n");
            }
        }
        return sb.append("\n## Ergebnis\n\nInhalt der Datei.\n").toString();
    }

    private static String code() {
        return "# Code-Kontext\n\n"
                + Texts.fileBlock("src/main/java/de/alt/AltRisikoRowMapper.java", "java",
                "class AltRisikoRowMapper {\n  Risiko mapRow(ResultSet rs) { return null; }\n}")
                + "\n" + Texts.fileBlock("src/main/java/de/alt/AltBestandDao.java", "java",
                "class AltBestandDao {\n  void ermittle(String vnr) { }\n}")
                + "\n" + Texts.fileBlock("src/main/resources/sql/select_alt_risiken.sql", "sql",
                "select * from ALT_RISIKO");
    }

    private static String readRow(String nr, String key, String rule, String need, String opl, String evidence) {
        return "| " + nr + " | " + key + " | Bedeutung | CHAR(2) | " + rule + " | Risiko.feld | " + need + " | " + opl
                + " | " + evidence + " |";
    }

    private static String readRow(int i) {
        return readRow("R" + i, key(i), "Wert wird gelesen", "[1:1]", "", OK_EVIDENCE);
    }

    private static String processRow(String nr, String key, String rule, String need, String opl, String evidence) {
        return "| " + nr + " | " + key + " | Herkunft | " + rule + " | " + need + " | " + opl + " | " + evidence + " |";
    }

    private static String ruleRow(String nr, String rule, String opl, String evidence) {
        return "| " + nr + " | " + rule + " | Selektion | [PRÜFEN] Filter | " + opl + " | " + evidence + " |";
    }

    private static final String READ_HEADER = "| Nr | Attribut | Bedeutung | Typ | Einlesen | Ziel im Lesemodell (Klasse.Feld) "
            + "| Prüfbedarf | OPL | Beleg |\n|---|---|---|---|---|---|---|---|---|\n";
    private static final String PROCESS_HEADER = "| Nr | Zielattribut | Herkunft (Quellattribut(e) bzw. „abgeleitet“) "
            + "| Mapping | Prüfbedarf | OPL | Beleg |\n|---|---|---|---|---|---|---|\n";
    private static final String RULE_HEADER = "| Nr | Regel | Art (Selektion, Sortierung/Gruppierung, Fehlerbehandlung) "
            + "| Prüfbedarf | OPL | Beleg |\n|---|---|---|---|---|---|\n";
    private static final String OPL_HEADER = "| OPL-Nr | Frage | Betrifft (Nr der Zeile bzw. Satzregel) | Warum offen "
            + "| Annahme bis zur Klärung |\n|---|---|---|---|---|\n";

    private static String opl(int... numbers) {
        StringBuilder sb = new StringBuilder("## Offene-Punkte-Liste (OPL)\n\n").append(OPL_HEADER);
        for (int n : numbers) {
            sb.append("| OPL-").append(n).append(" | Frage ").append(n).append(" | Zeile | unklar | keine |\n");
        }
        return sb.toString();
    }

    private static String summary(String status) {
        return "## Zusammenfassung\n\nStatus: " + status + "\n\nDie Entität wird gelesen.\n\n"
                + "## Ablaufübersicht\n\n| Schritt | Klasse | Aufgabe |\n|---|---|---|\n| Step 1 | AltRisikoRowMapper | lesen |\n\n";
    }

    private static String readSection(List<String> rows, List<String> rules) {
        return "## Phase Read\n\n" + READ_HEADER + String.join("\n", rows) + "\n\n" + RULE_HEADER
                + String.join("\n", rules) + "\n\n";
    }

    private static String processSection(List<String> rows) {
        return "## Phase Process\n\n" + PROCESS_HEADER + String.join("\n", rows) + "\n\n" + RULE_HEADER
                + ruleRow("SP1", "Nur aktive Verträge", "", "AltBestandDao.ermittle") + "\n\n";
    }

    private void prepare(String task) throws IOException {
        Files.writeString(dir.resolve("aufgabe-risiko.md"), task, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("code-risiko.md"), code(), StandardCharsets.UTF_8);
    }

    private Path result(String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private List<String> run() throws Exception {
        Job job = JobRegistry.find("spec-check").orElseThrow();
        List<String> log = new ArrayList<>();
        job.run(Config.of(job.params(), Map.of("run", dir.toString())), log::add);
        return log;
    }

    private String report() throws IOException {
        return Files.readString(dir.resolve("pruefbericht-risiko.md"), StandardCharsets.UTF_8);
    }

    private static String promptOf(List<String> log) {
        return log.stream().filter(l -> l.startsWith("Nachbesserungsprompt")).findFirst()
                .map(l -> l.substring(l.indexOf(": ") + 2)).orElse(null);
    }

    // ---------------------------------------------------------------- T12

    @Test
    void t12_completeResultHasNoFindings() throws Exception {
        prepare(task(5, 3));
        List<String> reads = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            reads.add(readRow(i));
        }
        reads.add(readRow("R3", key(3), "Wert wird gelesen", "[TECHNISCH]", "", OK_EVIDENCE));
        reads.add(readRow("H1", "ALT_OBJEKT.WOHNFLAECHE (Hilfswert)", "Nebentabelle", "[PRÜFEN] Basis", "", "AltBestandDao.ermittle"));
        reads.add(readRow("R4", key(4), "Wert wird gelesen", "[1:1]", "", "select_alt_risiken.sql"));
        reads.add(readRow("R5", key(5), "nicht gelesen", "[PRÜFEN] nicht gelesen", "OPL-1", ""));
        List<String> processes = List.of(
                processRow("P1", "ziel1", "direkt", "[1:1]", "", OK_EVIDENCE),
                processRow("P2", "ziel2", "FE → FEUER; sonst Fehler", "[PRÜFEN] Umschlüsselung", "OPL-2", "AltRisikoRowMapper.mapRow()"),
                processRow("P3", "ziel3", "nicht befüllt", "[PRÜFEN] nicht befüllt", "OPL-3", ""));
        result("spezifikation-risiko.md", summary("VOLLSTÄNDIG")
                + readSection(reads, List.of(ruleRow("SR1", "Nur aktive Verträge", "", "AltBestandDao.ermittle")))
                + processSection(processes) + opl(1, 2, 3));

        List<String> log = run();
        String report = report();

        assertEquals(7, count(report, "Keine Befunde."), report);
        assertTrue(log.contains("Read: Soll 5 · geliefert 5 · fehlend 0 · doppelt 0 · unbekannt 0 · Hilfswerte 1 · "
                + "Zeilen ohne gültigen Beleg 0 · OPL-Punkte 1"), String.join("\n", log));
        assertTrue(log.contains("Process: Soll 3 · geliefert 3 · fehlend 0 · doppelt 0 · unbekannt 0 · Hilfswerte 0 · "
                + "Zeilen ohne gültigen Beleg 0 · OPL-Punkte 2"), String.join("\n", log));
        assertTrue(log.stream().anyMatch(l -> l.contains("Befunde: D1 0 · D2 0 · D3 0 · D4 0 · D5 0 · D6 0 · D7 0")));
        assertNull(promptOf(log));
        assertFalse(Files.exists(dir.resolve("spezifikation-risiko-gesamt.md")), "single file is not merged");
        assertTrue(report.contains("nicht die fachliche Richtigkeit"));
    }

    // ---------------------------------------------------------------- T13

    @Test
    void t13_eachErrorIsReported() throws Exception {
        prepare(task(5, 3));
        List<String> reads = new ArrayList<>();
        reads.add(readRow("R1", key(1), "", "[1:1]", "", OK_EVIDENCE));                                 // empty Einlesen
        reads.add(readRow("R2", key(2), "Wert", "[1:1]", "", "GibtEsNicht.mapRow"));                  // unknown class
        reads.add(readRow("R2", key(2), "Wert", "[1:1]", "", OK_EVIDENCE));                           // duplicate
        reads.add(readRow("R3", key(3), "Wert", "[1:1]", "", "AltRisikoRowMapper.gibtEsNicht"));      // unknown method
        reads.add(readRow("R9", "ALT_RISIKO.FREMD", "Wert", "[1:1]", "", OK_EVIDENCE));               // unknown key
        reads.add(readRow("R5", key(5), "Wert", "egal", "", OK_EVIDENCE));                            // bad tag (D3)
        // R4 missing
        List<String> processes = List.of(
                processRow("P1", "ziel1", "direkt", "[1:1]", "OPL-9", OK_EVIDENCE),                    // OPL-9 missing
                processRow("P2", "ziel2", "FE → FEUER, ...", "[PRÜFEN] Umschlüsselung", "", OK_EVIDENCE), // ellipsis
                processRow("P3", "ziel3", "direkt", "[1:1]", "", OK_EVIDENCE));
        result("spezifikation-risiko.md", summary("VOLLSTÄNDIG")                                       // despite R4
                + readSection(reads, List.of(ruleRow("SR1", "Nur aktive Verträge", "", OK_EVIDENCE)))
                + processSection(processes) + opl(1));

        List<String> log = run();
        String report = report();
        String[] sections = report.split("(?m)^## ");
        String d1 = section(sections, "D1");

        assertTrue(d1.contains("| Read | R4 | ALT_RISIKO.C4 | fehlend |"), d1);
        assertTrue(d1.contains("| Read | R2 | ALT_RISIKO.C2 | doppelt (2x) |"), d1);
        assertTrue(d1.contains("ALT_RISIKO.FREMD | unbekannt"), d1);
        assertTrue(section(sections, "D2").contains("| Read | R1 | ALT_RISIKO.C1 | Pflichtzelle leer: Einlesen |"), report);
        assertTrue(section(sections, "D3").contains("| R5 | ALT_RISIKO.C5 |"), report);
        String d4 = section(sections, "D4");
        assertTrue(d4.contains("| R2 | ALT_RISIKO.C2 | Klasse GibtEsNicht nicht im Code-Kontext |"), d4);
        assertTrue(d4.contains("| R3 | ALT_RISIKO.C3 | Methode gibtEsNicht nicht in AltRisikoRowMapper |"), d4);
        assertTrue(section(sections, "D5").contains("| Process | P1 | ziel1 | Verweis auf OPL-9"), report);
        assertTrue(section(sections, "D5").contains("Hinweis: OPL-Punkt ohne Verweis"), "OPL-1 is unreferenced");
        assertTrue(section(sections, "D6").contains("| Process | P2 | ziel2 | Auslassung „...“"), report);
        assertTrue(section(sections, "D7").contains("VOLLSTÄNDIG trotz Befund in D1"), report);

        String prompt = promptOf(log);
        assertNotNull(prompt, String.join("\n", log));
        assertTrue(prompt.startsWith("In spezifikation-risiko.md sind diese Zeilen fehlerhaft."), prompt);
        assertTrue(prompt.contains("Fehlend: R4."), prompt);
        assertTrue(prompt.contains("Doppelt: R2."), prompt);
        assertTrue(prompt.contains("Beleg steht nicht im Code-Kontext: R2: GibtEsNicht.mapRow; R3: AltRisikoRowMapper.gibtEsNicht."), prompt);
        assertTrue(prompt.contains("Pflichtzelle leer: R1."), prompt);
        assertTrue(prompt.contains("spezifikation-risiko-korrektur.md"), prompt);
        assertTrue(prompt.length() <= 1500);
        assertTrue(log.stream().anyMatch(l -> l.contains("Befunde: D1 3 · D2 1 · D3 1 · D4 2 · D5 1 · D6 1 · D7 1")),
                String.join("\n", log));
    }

    @Test
    void t13b_promptOmitsCategoriesWithoutFindingsAndStaysShort() throws Exception {
        prepare(task(300, 0));
        result("spezifikation-risiko.md", summary("UNVOLLSTÄNDIG – letzte vollständige Zeile: R1")
                + readSection(List.of(readRow(1)), List.of()) + opl(1));

        List<String> log = run();
        String prompt = promptOf(log);

        assertNotNull(prompt);
        assertTrue(prompt.contains("Fehlend: R2, R3"), prompt);
        assertFalse(prompt.contains("Doppelt:"), prompt);
        assertFalse(prompt.contains("Beleg steht nicht"), prompt);
        assertFalse(prompt.contains("Pflichtzelle leer:"), prompt);
        assertTrue(prompt.length() <= 1500, "length " + prompt.length());
        assertTrue(prompt.contains(" u. a."), prompt);
        assertTrue(log.stream().anyMatch(l -> l.contains("weiterer Durchgang")), String.join("\n", log));
    }

    @Test
    void evidenceFormsAndRuleExceptions() throws Exception {
        prepare(task(4, 0));
        List<String> reads = List.of(
                readRow("R1", key(1), "Wert", "[1:1]", "", "1: AltRisikoRowMapper.mapRow · 2: AltBestandDao.ermittle"),
                readRow("R2", key(2), "Wert", "[1:1]", "", "select_alt_risiken.sql, AltBestandDao.ermittle(vnr, art)"),
                readRow("R3", key(3), "Wert", "[1:1]", "", "gibt_es_nicht.sql"),
                readRow("R4", key(4), "nicht belegt", "[PRÜFEN] nicht belegt", "", "irgendein Hinweis"));
        result("spezifikation-risiko.md", summary("VOLLSTÄNDIG") + readSection(reads, List.of()) + opl());

        List<String> log = run();
        String d4 = section(report().split("(?m)^## "), "D4");

        assertTrue(d4.contains("| R3 | ALT_RISIKO.C3 | Datei gibt_es_nicht.sql nicht im Code-Kontext |"), d4);
        assertTrue(d4.contains("| R4 | ALT_RISIKO.C4 | Beleg nicht prüfbar"), d4);
        assertEquals(2, count(d4, "ALT_RISIKO.C"), d4);
        assertTrue(promptOf(log).contains("R3: gibt_es_nicht.sql"));
    }

    private static void assertRowHas(String text, String nr, String fragment) {
        String row = text.lines().filter(l -> l.startsWith("| " + nr + " |")).findFirst().orElseThrow();
        assertTrue(row.contains(fragment), row);
    }

    private static String section(String[] sections, String start) {
        for (String s : sections) {
            if (s.startsWith(start)) {
                return s;
            }
        }
        return fail("section " + start);
    }

    // ---------------------------------------------------------------- T14

    @Test
    void t14_mergesMainContinuationAndCorrection() throws Exception {
        prepare(task(8, 0));
        Path main = result("spezifikation-risiko.md", summary("UNVOLLSTÄNDIG – letzte vollständige Zeile: R5")
                + readSection(List.of(readRow(1), readRow("R2", key(2), "alter Text", "[1:1]", "", OK_EVIDENCE),
                readRow(3), readRow(4), readRow(5)),
                List.of(ruleRow("SR1", "Nur aktive Verträge", "OPL-1", OK_EVIDENCE))) + opl(1));
        Path part2 = result("spezifikation-risiko-teil2.md", "## Zusammenfassung\n\nStatus: VOLLSTÄNDIG\n\nTeil 2.\n\n"
                + readSection(List.of(readRow(6), readRow("R7", key(7), "Wert", "[1:1]", "OPL-1", OK_EVIDENCE),
                readRow(8)), List.of(ruleRow("SR1", "Sortiert nach VNR", "", OK_EVIDENCE))) + opl(1));
        Path fix = result("spezifikation-risiko-korrektur.md", "## Phase Read\n\n" + READ_HEADER
                + readRow("R2", key(2), "neuer Text", "[PRÜFEN] Filter", "OPL-1", OK_EVIDENCE) + "\n\n"
                + opl(1));
        Files.setLastModifiedTime(part2, FileTime.fromMillis(1_000_000));
        Files.setLastModifiedTime(fix, FileTime.fromMillis(2_000_000));
        Files.setLastModifiedTime(main, FileTime.fromMillis(3_000_000));
        byte[][] before = {Files.readAllBytes(main), Files.readAllBytes(part2), Files.readAllBytes(fix)};

        List<String> log = run();
        String all = Files.readString(dir.resolve("spezifikation-risiko-gesamt.md"), StandardCharsets.UTF_8);

        for (int i = 1; i <= 8; i++) {
            assertEquals(1, count(all, "| R" + i + " |"), "R" + i + "\n" + all);
        }
        int last = -1;
        for (int i = 1; i <= 8; i++) {
            int at = all.indexOf("| R" + i + " |");
            assertTrue(at > last, "order R" + i);
            last = at;
        }
        assertTrue(all.contains("neuer Text") && !all.contains("alter Text"), all);
        assertTrue(all.contains("| SR1 | Nur aktive Verträge") && all.contains("| SR2 | Sortiert nach VNR"), all);
        // OPL: main 1, part 2 becomes 2, correction becomes 3; references follow
        assertEquals(1, count(all, "\n| OPL-1 |"));
        assertEquals(1, count(all, "\n| OPL-2 |"));
        assertEquals(1, count(all, "\n| OPL-3 |"));
        assertRowHas(all, "R2", "| OPL-3 |");
        assertRowHas(all, "R7", "| OPL-2 |");
        assertTrue(all.contains("| SR1 | Nur aktive Verträge | Selektion | [PRÜFEN] Filter | OPL-1 |"), all);
        assertTrue(all.contains("Status: VOLLSTÄNDIG"), all);
        assertTrue(count(report(), "Keine Befunde.") == 7, report());
        assertTrue(log.stream().anyMatch(l -> l.contains("Befunde: D1 0 · D2 0 · D3 0 · D4 0 · D5 0 · D6 0 · D7 0")),
                String.join("\n", log));
        assertArrayEquals(before[0], Files.readAllBytes(main));
        assertArrayEquals(before[1], Files.readAllBytes(part2));
        assertArrayEquals(before[2], Files.readAllBytes(fix));

        // the merged file is no input of a second run
        List<String> again = run();
        assertTrue(again.get(0).contains("3 Ergebnisdatei(en)"), again.get(0));
    }

    // ---------------------------------------------------------------- T15

    @Test
    void t15_oneRunPerPhaseWithOwnOpl() throws Exception {
        prepare(task(2, 2));
        result("spezifikation-risiko.md", summary("VOLLSTÄNDIG")
                + readSection(List.of(readRow("R1", key(1), "Wert", "[1:1]", "OPL-1", OK_EVIDENCE), readRow(2)),
                List.of()) + opl(1));
        Path process = result("spezifikation-risiko-process.md", summary("VOLLSTÄNDIG")
                + processSection(List.of(processRow("P1", "ziel1", "direkt", "[1:1]", "OPL-1", OK_EVIDENCE),
                processRow("P2", "ziel2", "direkt", "[1:1]", "", OK_EVIDENCE))) + opl(1));
        Files.setLastModifiedTime(process, FileTime.fromMillis(System.currentTimeMillis() + 5000));

        List<String> log = run();
        String all = Files.readString(dir.resolve("spezifikation-risiko-gesamt.md"), StandardCharsets.UTF_8);

        assertTrue(all.contains("## Phase Read") && all.contains("## Phase Process"), all);
        assertEquals(1, count(all, "\n| OPL-1 |"));
        assertEquals(1, count(all, "\n| OPL-2 |"));
        assertRowHas(all, "P1", "| OPL-2 |");
        assertRowHas(all, "R1", "| OPL-1 |");
        assertTrue(log.stream().anyMatch(l -> l.contains("Befunde: D1 0 · D2 0 · D3 0 · D4 0 · D5 0 · D6 0 · D7 0")),
                String.join("\n", log));
    }

    // ---------------------------------------------------------------- T16

    @Test
    void t16_missingInputsGiveClearMessages() throws Exception {
        Files.writeString(dir.resolve("aufgabe-risiko.md"), task(1, 0), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("code-risiko.md"), code(), StandardCharsets.UTF_8);
        IllegalArgumentException noResult = assertThrows(IllegalArgumentException.class, this::run);
        assertTrue(noResult.getMessage().contains("Ergebnis von Copilot"), noResult.getMessage());

        result("spezifikation-risiko-gesamt.md", "# nur das Ergebnis eines früheren Laufs");
        assertThrows(IllegalArgumentException.class, this::run);

        Files.delete(dir.resolve("aufgabe-risiko.md"));
        result("spezifikation-risiko.md", summary("VOLLSTÄNDIG"));
        IllegalArgumentException noTask = assertThrows(IllegalArgumentException.class, this::run);
        assertTrue(noTask.getMessage().contains("Aufgabe"), noTask.getMessage());

        Files.writeString(dir.resolve("aufgabe-risiko.md"), task(1, 0), StandardCharsets.UTF_8);
        Files.delete(dir.resolve("code-risiko.md"));
        IllegalArgumentException noCode = assertThrows(IllegalArgumentException.class, this::run);
        assertTrue(noCode.getMessage().contains("Code-Kontext"), noCode.getMessage());
    }

    @Test
    void registeredWithRunParameter() {
        Job job = JobRegistry.find("spec-check").orElseThrow();
        assertEquals(List.of("run"), job.params().stream().map(p -> p.key()).collect(Collectors.toList()));
        assertTrue(job.params().getFirst().required());
    }
}
