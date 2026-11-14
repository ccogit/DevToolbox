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
