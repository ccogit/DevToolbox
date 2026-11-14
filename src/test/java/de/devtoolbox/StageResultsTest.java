package de.devtoolbox;

import de.devtoolbox.jobs.phasespec.LogicInventory;
import de.devtoolbox.jobs.phasespec.StageResults;
import de.devtoolbox.core.Texts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The last stage of the variant "in Etappen" and the restriction of "Soll Logik" to classes of the entity. */
class StageResultsTest {

    @TempDir
    Path dir;

    @Test
    void closingTaskIsTheTaskWithTheProtocolOfAllStages() throws Exception {
        Path run = Files.createDirectories(dir.resolve("lauf"));
        Path downloads = Files.createDirectories(dir.resolve("downloads"));
        Path task = Files.writeString(run.resolve("aufgabe-x.md"), "# Aufgabe\n\n## Soll-Listen\n\n| P1 |\n");
        Path one = Files.writeString(downloads.resolve("spezifikation-x-e-logik-1 (2).md"), "## Logikprotokoll\r\n\r\n### L1 A.a\n");
        Path two = Files.writeString(downloads.resolve("spezifikation-x-e-logik-2.md"), "## Logikprotokoll\n\n### L2 B.b\n");

        Path closing = StageResults.prepareClosing(task, List.of(one, two),
                List.of("spezifikation-x-e-logik-1.md", "spezifikation-x-e-logik-2.md"));

        assertEquals(run.resolve("aufgabe-x-abschluss.md"), closing);
        String text = Files.readString(closing);
        assertTrue(text.startsWith("# Aufgabe") && text.contains("| P1 |"), text);
        assertTrue(text.contains("## Logikprotokoll der Etappen 1 bis 2"), text);
        assertTrue(text.indexOf("### L1 A.a") < text.indexOf("### L2 B.b"), text);
        assertTrue(text.contains("===== BEGIN FILE: spezifikation-x-e-logik-1.md ====="), text);
        assertFalse(text.contains("\r"));
        // the answers lie in the run folder under the names they were asked for
        assertTrue(Files.exists(run.resolve("spezifikation-x-e-logik-1.md")));
        assertTrue(Files.exists(run.resolve("spezifikation-x-e-logik-2.md")));
        assertEquals("# Aufgabe\n\n## Soll-Listen\n\n| P1 |\n", Files.readString(task), "the task itself stays");
    }

    private static final String STAGE_1 = """
            ## Logikprotokoll

            ### L1 A.a
            | Nr | Bedingung | Ergebnis | Original | Betrifft |
            |---|---|---|---|---|
            | L1.1 | x leer | Default | `a == null \\|\\| a.isBlank()` | P2 |
            | L1.2 | sonst | Fehler | `else` | Satz |

            ## Offene Punkte dieser Etappe

            - Regel L1.1: Lookup fehlt.
            """;
    private static final String STAGE_2 = """
            ## Logikprotokoll

            ### L2 B.b
            | Nr | Bedingung | Ergebnis | Original | Betrifft |
            |---|---|---|---|---|
            | L2.1 | immer | Wert / 100 | `w / 100` | P10, P2 |
            | L2.2 | nie | nichts | `false` | – |
            """;

    /** The closing task lists per target row the rules that concern it, counted from the column "Betrifft". */
    @Test
    void closingTaskIndexesTheRulesByTargetRow() throws Exception {
        Path task = Files.writeString(dir.resolve("aufgabe-x.md"), "# Aufgabe\n");
        Path one = Files.writeString(dir.resolve("a.md"), STAGE_1);
        Path two = Files.writeString(dir.resolve("b.md"), STAGE_2);
        String text = Files.readString(StageResults.prepareClosing(task, List.of(one, two),
                List.of("spezifikation-x-e-logik-1.md", "spezifikation-x-e-logik-2.md")));
        assertTrue(text.contains("### Regeln je Soll-Zeile"), text);
        assertTrue(text.contains("| P2 | L1.1, L2.1 |\n| P10 | L2.1 |"), text);
        assertTrue(text.contains("Regeln über den ganzen Satz (jede in genau einer Satzregel belegen): L1.2\n"), text);
    }

    /** One document: the Logikprotokoll of all stages before the tables of the last stage. */
    @Test
    void mergePutsTheProtocolBeforeTheTablesOfThePhases() throws Exception {
        Path one = Files.writeString(dir.resolve("a.md"), STAGE_1);
        Path two = Files.writeString(dir.resolve("b.md"), STAGE_2);
        Path last = Files.writeString(dir.resolve("spezifikation-x-e (2).md"),
                "## Zusammenfassung\n\nStatus: VOLLSTÄNDIG\n\n## Ablaufübersicht\n\n| 1 |\n\n## Phase Process\n\n| P2 |\n\n"
                        + "## Offene-Punkte-Liste (OPL)\n\n| OPL-1 |\n");
        Path run = Files.createDirectories(dir.resolve("lauf"));

        Path merged = StageResults.merge(last, List.of(one, two), run, "spezifikation-x-e.md");

        assertEquals(run.resolve("spezifikation-x-e-gesamt.md"), merged);
        String text = Files.readString(merged);
        int protocol = text.indexOf("## Logikprotokoll");
        assertTrue(text.indexOf("## Ablaufübersicht") < protocol && protocol < text.indexOf("## Phase Process"), text);
        assertEquals(1, text.split("## Logikprotokoll", -1).length - 1, "one heading for all stages");
        assertTrue(text.indexOf("### L1 A.a") < text.indexOf("### L2 B.b"), text);
        assertTrue(text.contains("### Offene Punkte der Etappen (wie gemeldet)\n\n**Etappe 1**\n\n- Regel L1.1: Lookup fehlt."), text);
        assertTrue(text.indexOf("Lookup fehlt") < text.indexOf("## Phase Process"), text);
        assertTrue(text.strip().endsWith("| OPL-1 |"), text);

        // an answer without the expected headings: the protocol is appended
        Path odd = Files.writeString(dir.resolve("odd.md"), "# Status\n\nUNVOLLSTÄNDIG\n");
        String appended = Files.readString(StageResults.merge(odd, List.of(one), run, "spezifikation-y-e.md"));
        assertTrue(appended.startsWith("# Status") && appended.contains("## Logikprotokoll\n\n### L1 A.a"), appended);
    }

    @Test
    void namesOfStagesAndOfTheClosingTask() {
        assertTrue(StageResults.isStageFile("spezifikation-x-e-logik-12.md"));
        assertFalse(StageResults.isStageFile("spezifikation-x-e.md"));
        assertEquals("aufgabe-x-abschluss.md", StageResults.closingTaskName("aufgabe-x.md"));
    }

    @Test
    void sollLogikOnlyCountsClassesMentioningTheEntity() {
        String code = Texts.fileBlock("src/NachlassMapper.java", "java",
                "class NachlassMapper { int m(int a) { if (a > 2) { return 3; } return 4; } }")
                + Texts.fileBlock("src/util/TimestampConverter.java", "java",
                "class TimestampConverter { String c(Object t) { if (t == null) { return null; } return t.toString(); } }");
        assertEquals(2, LogicInventory.of(code).methods().size());
        LogicInventory entity = LogicInventory.of(code, List.of("Nachlass", " Rabatt "));
        assertEquals(List.of("NachlassMapper.m"), entity.methods().stream().map(LogicInventory.Method::key).toList());
        assertEquals(2, entity.files(), "the number of files in the code file stays");
    }
}
