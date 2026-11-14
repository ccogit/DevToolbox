package de.devtoolbox.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The strip of a finished run: one copy button per prompt, one link per file. */
class RunResultsTest {

    @org.junit.jupiter.api.io.TempDir
    Path downloads;

    @Test
    void answerNamesComeFromTheLogLineOfTheJob() {
        assertEquals(List.of("spezifikation-x-e-logik-1.md", "spezifikation-x-e.md"), RunResults.answerNames(List.of(
                "Ordner: C:\\x", "Antwortdateien von Copilot: spezifikation-x-e-logik-1.md, spezifikation-x-e.md")));
        assertTrue(RunResults.answerNames(List.of("Fertig in 3 ms.")).isEmpty());
    }

    @Test
    void downloadIsTheNewestCopyOfTheRun() throws Exception {
        long since = 1_000_000L;
        assertTrue(RunResults.download(downloads, "spezifikation-x.md", since).isEmpty());
        file("spezifikation-x.md", since - 5_000);          // from an earlier run
        file("spezifikation-x-teil2.md", since + 9_000);    // another file
        assertTrue(RunResults.download(downloads, "spezifikation-x.md", since).isEmpty());
        Path first = file("spezifikation-x (1).md", since + 1_000);
        assertEquals(first, RunResults.download(downloads, "spezifikation-x.md", since).orElseThrow());
        Path second = file("spezifikation-x (2).md", since + 2_000);
        assertEquals(second, RunResults.download(downloads, "spezifikation-x.md", since).orElseThrow());
        assertTrue(RunResults.download(downloads.resolve("fehlt"), "spezifikation-x.md", since).isEmpty());
    }

    @Test
    void answerBecomesALinkOnceItIsDownloaded() throws Exception {
        long since = 1_000_000L;
        List<Path> opened = new ArrayList<>();
        RunResults[] results = new RunResults[1];
        SwingUtilities.invokeAndWait(() -> {
            results[0] = new RunResults(List.of(), List.of(), null,
                    new RunResults.Answers(List.of("spezifikation-x.md"), downloads, since), _ -> { }, opened::add);
            JButton answer = button(results[0], "answer:spezifikation-x.md");
            assertEquals("spezifikation-x.md (fehlt noch)", answer.getText());
            assertTrue(!answer.isEnabled() && answer.getToolTipText().startsWith("Noch nicht im Download-Ordner"));
        });
        Path file = file("spezifikation-x.md", since + 1_000);
        SwingUtilities.invokeAndWait(() -> {
            results[0].refreshAnswers();
            JButton answer = button(results[0], "answer:spezifikation-x.md");
            assertTrue(answer.isEnabled() && answer.getText().contains("<u>"));
            answer.doClick();
            assertEquals(List.of(file), opened);
        });
    }

    private Path file(String name, long modified) throws Exception {
        Path file = java.nio.file.Files.writeString(downloads.resolve(name), "x");
        java.nio.file.Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(modified));
        return file;
    }

    @Test
    void oneLabelledPromptPerLogLine() {
        List<RunResults.Prompt> prompts = RunResults.prompts(List.of(
                "Ordner: C:\\x",
                "Prompt: Bearbeite Etappe 1: von L1 bis L4.",
                "Prompt Etappe 2 von 3: Bearbeite Etappe 2.",
                "Prompt Etappe 3 von 3 (Abschluss): Tabellen und OPL.",
                "Prompt, wenn eine Etappe abbricht: Setze fort.",
                "Größe gesamt: 1 Zeichen"));
        assertEquals(List.of("Etappe 1", "Etappe 2", "Etappe 3 (Abschluss)", "wenn eine Etappe abbricht"),
                prompts.stream().map(RunResults.Prompt::label).toList());
        assertEquals("Bearbeite Etappe 1: von L1 bis L4.", prompts.getFirst().text());
        assertEquals("Setze fort.", prompts.getLast().text());
    }

    @Test
    void withoutStagesTheFirstPromptIsTheStart() {
        List<RunResults.Prompt> prompts = RunResults.prompts(List.of(
                "Prompt: Bearbeite die Aufgabe.", "Prompt bei UNVOLLSTÄNDIG: Setze fort."));
        assertEquals(List.of("Start", "bei UNVOLLSTÄNDIG"), prompts.stream().map(RunResults.Prompt::label).toList());
        assertTrue(RunResults.prompts(List.of("Copilot-Prompt kopiert.", "Fertig in 3 ms.")).isEmpty());
    }

    @Test
    void buttonsCopyTheirPromptAndLinksOpenTheirFile() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<RunResults.Prompt> prompts = List.of(new RunResults.Prompt("Etappe 1", "eins"),
                    new RunResults.Prompt("Etappe 2", "zwei"));
            Path folder = Path.of("lauf");
            List<String> copied = new ArrayList<>();
            List<Path> opened = new ArrayList<>();
            RunResults results = new RunResults(prompts, List.of(folder.resolve("aufgabe.md")), folder,
                    p -> copied.add(p.text()), opened::add);

            JButton second = button(results, "prompt:Etappe 2");
            second.doClick();
            assertEquals(List.of("zwei"), copied);
            assertEquals("✓ Etappe 2", second.getText());
            assertEquals("Etappe 1", button(results, "prompt:Etappe 1").getText());

            button(results, "open:aufgabe.md").doClick();
            button(results, "open:Ordner").doClick();
            assertEquals(List.of(folder.resolve("aufgabe.md"), folder), opened);

            assertTrue(new RunResults(List.of(), List.of(), null, _ -> { }, _ -> { }).isEmpty());
        });
    }

    private static JButton button(Container parent, String name) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JButton b && name.equals(b.getName())) {
                return b;
            }
            if (c instanceof Container inner) {
                try {
                    return button(inner, name);
                } catch (AssertionError notHere) {
                    // look in the next row
                }
            }
        }
        throw new AssertionError("no button " + name);
    }
}
