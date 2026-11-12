package de.devtoolbox;

import de.devtoolbox.core.BatchRunner;
import de.devtoolbox.core.Job;
import de.devtoolbox.core.JobRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.devtoolbox.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** TESTFAELLE.md cases I1, I2, I3, I4, I5, I7, I8. */
class ImplementationJobTest {

    private static final Path STANDARDS = PROJEKTE.resolve("testziel/standards");

    @TempDir
    Path out;

    private Map<String, String> values(Path standards) {
        Map<String, String> v = new HashMap<>();
        v.put("standards", standards.toString());
        v.put("spec", SPEC_PDF.toString());
        v.put("entity", "Risiko");
        v.put("targetModel", ZIELMODELL.toString());
        return v;
    }

    @Test
    void i1_allPhases() throws Exception {
        var r = run("implementation", values(STANDARDS), out);
        assertTrue(r.logText().contains("Standards: 8 von 8 Vorlagen für Read, Process, Write (Beispiel-Entität Vertrag)"), r.logText());
        assertEquals(List.of("skill-implementierung.md", "aufgabe-implementierung-risiko.md", "spezifikation-risiko.pdf"), r.names());
        String task = r.read("aufgabe-implementierung-risiko.md");
        assertTrue(task.contains("`RisikoTO`"));
        assertTrue(task.contains("src/main/java/de/vers/neu/risiko/to/RisikoTO.java"));
        assertTrue(task.contains("`RisikoWriter`"));
        assertTrue(task.contains("Anreicherer<RisikoTO>"));
        assertTrue(task.contains("## Standards"));
        assertEquals(8, count(task, "### Vorlage "));
        assertTrue(task.contains("AbstractTO") && task.contains("AbstractPO") && task.contains("Anreicherer"));
        assertTrue(task.contains("implementierung-risiko.zip"));
        assertTrue(Files.size(r.out().resolve("spezifikation-risiko.pdf")) > 0);
        assertEquals(out.resolve("devtoolbox/ohne-projekt-implementierung-risiko"), r.out());
    }

    @Test
    void i2_onlyWrite() throws Exception {
        Map<String, String> v = values(STANDARDS);
        v.put("phases", "WRITE");
        var r = run("implementation", v, out);
        assertTrue(r.logText().contains("Standards: 1 von 8 Vorlagen für Write"), r.logText());
        String task = r.read("aufgabe-implementierung-risiko.md");
        assertTrue(task.contains("`RisikoWriter`"));
        assertFalse(task.contains("`RisikoPoRowMapper`"));
        assertFalse(task.contains("## Zielattribute"));
    }

    @Test
    void i3_editedStandards(@TempDir Path copy) throws Exception {
        Path std = copy.resolve("standards");
        copyTree(STANDARDS, std);
        Path index = std.resolve("STANDARDS.md");
        List<String> lines = new ArrayList<>(Files.readAllLines(index));
        int first = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("| 1 |")) {
                first = i;
            }
        }
        assertTrue(first > 0);
        lines.set(first, lines.get(first).replaceFirst("\\|\\s*\\|$", "| HINWEIS-ABC |"));
        lines.removeIf(l -> l.startsWith("| 5 |"));
        Files.write(index, lines);
        Map<String, String> v = values(std);
        v.put("phases", "READ");
        var r = run("implementation", v, out);
        assertTrue(r.logText().contains("Standards: 5 von 7 Vorlagen für Read"), r.logText());
        assertTrue(r.read("aufgabe-implementierung-risiko.md").contains("HINWEIS-ABC"));
    }

    @Test
    void i4_noStandards(@TempDir Path empty) {
        Exception e = assertThrows(IllegalArgumentException.class, () -> run("implementation", values(empty), out));
        assertTrue(e.getMessage().contains("Keine Standards in"), e.getMessage());
    }

    @Test
    void i5_specValidation(@TempDir Path tmp) throws Exception {
        Path txt = tmp.resolve("spec.txt");
        Files.writeString(txt, "# keine Spezifikation");
        Map<String, String> v = values(STANDARDS);
        v.put("spec", txt.toString());
        Exception e = assertThrows(IllegalArgumentException.class, () -> run("implementation", v, out));
        assertTrue(e.getMessage().contains("Markdown- (.md) oder PDF-Datei"), e.getMessage());

        Path fake = tmp.resolve("fake.pdf");
        Files.writeString(fake, "kein pdf");
        v.put("spec", fake.toString());
        e = assertThrows(IllegalArgumentException.class, () -> run("implementation", v, out));
        assertTrue(e.getMessage().contains("keine gültige PDF-Datei"), e.getMessage());
    }

    @Test
    void i6_markdownSpecAccepted(@TempDir Path tmp) throws Exception {
        Path md = tmp.resolve("meine spec.md");
        Files.writeString(md, "# Spezifikation Risiko\n\nÄnderung des Fachbereichs");
        Map<String, String> v = values(STANDARDS);
        v.put("spec", md.toString());
        var r = run("implementation", v, out);
        assertEquals(List.of("skill-implementierung.md", "aufgabe-implementierung-risiko.md", "spezifikation-risiko.md"), r.names());
        assertTrue(r.read("spezifikation-risiko.md").contains("Änderung des Fachbereichs"));
        assertTrue(r.read("aufgabe-implementierung-risiko.md").contains("`spezifikation-risiko.md`"));
    }

    @Test
    void specInsideRunFolderSurvivesRerun() throws Exception {
        var first = run("implementation", values(STANDARDS), out);
        Map<String, String> v = values(STANDARDS);
        v.put("spec", first.out().resolve("spezifikation-risiko.pdf").toString());
        var again = run("implementation", v, out);
        assertTrue(Files.size(again.out().resolve("spezifikation-risiko.pdf")) > 0);
    }

    @Test
    void copilotLimit() throws Exception {
        Map<String, String> v = values(STANDARDS);
        v.put("copilotLimit", "500");
        var r = run("implementation", v, out);
        assertTrue(r.logText().contains("WARNUNG: Geschätzt ca."), r.logText());
        assertTrue(r.logText().contains("PDF-Spezifikation"), r.logText());
        assertTrue(r.logText().contains("weniger Phasen"), r.logText());
        // C1/E3: size report for the text files, the PDF is named but not counted
        assertTrue(r.logText().contains("Größe skill-implementierung.md: "), r.logText());
        assertTrue(r.logText().contains("Größe aufgabe-implementierung-risiko.md: "), r.logText());
        assertTrue(r.logText().contains("Größe spezifikation-risiko.pdf: PDF, nicht gezählt"), r.logText());
        assertTrue(r.logText().contains("Größe gesamt: "), r.logText());
        // E2: the prompt asks to report an incomplete result
        assertTrue(r.logText().contains("melde Status: UNVOLLSTÄNDIG und was fehlt."), r.logText());
        assertTrue(r.read("skill-implementierung.md").contains("**Unvollständigkeit melden.**"));
        // C2: without a value the default limit applies, 0 switches the check off
        var standard = run("implementation", values(STANDARDS), out.resolve("standard"));
        assertTrue(standard.logText().contains("Copilot-Limit 30.000") || standard.logText().contains("Copilot-Limit 30,000"),
                standard.logText());
        Map<String, String> off = values(STANDARDS);
        off.put("copilotLimit", "0");
        assertFalse(run("implementation", off, out.resolve("aus")).logText().contains("Copilot-Limit"));
    }

    @Test
    void batchRunnerForEntities() throws Exception {
        Job job = JobRegistry.find("implementation").orElseThrow();
        List<String> log = new ArrayList<>();
        Map<String, String> base = values(STANDARDS);
        base.remove("entity");
        base.put("output", out.toString());
        var files = BatchRunner.run(job, base, List.of(new BatchRunner.Entity("Risiko"),
                new BatchRunner.Entity(""), new BatchRunner.Entity("Vertrag")), log::add);
        String text = String.join("\n", log);
        assertEquals(6, files.size(), text);
        assertTrue(text.contains("FEHLER bei Entität "), text);
        assertTrue(text.contains("Zusammenfassung: 2 von 3"), text);
        assertTrue(Files.exists(out.resolve("devtoolbox/ohne-projekt-implementierung-vertrag/aufgabe-implementierung-vertrag.md")));
    }

    @Test
    void i7_targetAttributes() throws Exception {
        var r = run("implementation", values(STANDARDS), out);
        assertTrue(r.logText().contains("Zielattribute: 11 von Risiko aus Risiko.json"), r.logText());
        String task = r.read("aufgabe-implementierung-risiko.md");
        assertTrue(task.contains("## Zielattribute (Zielmodell)"));
        assertTrue(task.contains("| 11 | lage.ort |"));
        assertTrue(task.contains("Umsetzung je Attribut"));
    }

    @Test
    void i8_readOnlyAndProcessWithoutModel() throws Exception {
        Map<String, String> v = values(STANDARDS);
        v.put("phases", "READ");
        v.remove("targetModel");
        var r = run("implementation", v, out);
        assertFalse(r.read("aufgabe-implementierung-risiko.md").contains("## Zielattribute"));

        Map<String, String> w = values(STANDARDS);
        w.remove("targetModel");
        Exception e = assertThrows(IllegalArgumentException.class, () -> run("implementation", w, out));
        assertTrue(e.getMessage().contains("Zielmodell"), e.getMessage());
    }

    @Test
    void projectTemplatesExistingAreOnlyUsed() throws Exception {
        Map<String, String> v = values(STANDARDS);
        v.put("target", ZIEL.toString());
        var r = run("implementation", v, out);
        assertTrue(r.logText().contains("Vorlagen je Projekt: 3, davon 0 anzulegen, 3 vorhanden"), r.logText());
        String task = r.read("aufgabe-implementierung-risiko.md");
        assertTrue(task.contains("## Vorlagen je Projekt (einmalig)"));
        assertTrue(task.contains("| `AbstractPO` | vorhanden – verwenden, nicht neu anlegen | `src/main/java/de/vers/neu/common/AbstractPO.java` |"), task);
        assertFalse(task.contains("anzulegen (einmalig)"));
    }

    @Test
    void projectTemplatesMissingAreCreatedOnce(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("src/main/java/de/vers/neu/common"));
        Files.writeString(root.resolve("src/main/java/de/vers/neu/common/AbstractTO.java"), "package de.vers.neu.common; public class AbstractTO {}");
        // ignored: a copy below target/
        Files.createDirectories(root.resolve("target/classes"));
        Files.writeString(root.resolve("target/classes/AbstractPO.java"), "class AbstractPO {}");
        Map<String, String> v = values(STANDARDS);
        v.put("target", root.toString());
        var r = run("implementation", v, out);
        assertTrue(r.logText().contains("davon 2 anzulegen, 1 vorhanden"), r.logText());
        String task = r.read("aufgabe-implementierung-risiko.md");
        assertTrue(task.contains("| `AbstractPO` | anzulegen (einmalig) | `src/main/java/de/vers/neu/common/AbstractPO.java` |"), task);
        assertTrue(task.contains("| `Anreicherer` | anzulegen (einmalig) |"), task);
        assertTrue(task.contains("| `AbstractTO` | vorhanden – verwenden, nicht neu anlegen | `src/main/java/de/vers/neu/common/AbstractTO.java` |"), task);
        assertTrue(task.contains("#### AbstractPO – anzulegen (einmalig)"));
    }

    @Test
    void sameNamedClassInWrongPackageDoesNotSatisfyProjectTemplate(@TempDir Path root) throws Exception {
        Path wrong = Files.createDirectories(root.resolve("src/main/java/de/other"));
        Files.writeString(wrong.resolve("AbstractTO.java"),
                "package de.other; public abstract class AbstractTO {}");
        Map<String, String> v = values(STANDARDS);
        v.put("target", root.toString());
        var result = run("implementation", v, out);
        String task = result.read("aufgabe-implementierung-risiko.md");
        assertTrue(task.contains("| `AbstractTO` | anzulegen (einmalig) | "
                + "`src/main/java/de/vers/neu/common/AbstractTO.java` |"), task);
        assertTrue(result.logText().contains("davon 3 anzulegen, 0 vorhanden"), result.logText());
    }

    @Test
    void projectTemplatesWithoutRootAreToBeChecked() throws Exception {
        var r = run("implementation", values(STANDARDS), out);
        assertTrue(r.logText().contains("nicht geprüft"), r.logText());
        String task = r.read("aufgabe-implementierung-risiko.md");
        assertTrue(task.contains("prüfen ob vorhanden"));
        assertTrue(task.contains("Code des Migrationsprojekts wurde nicht angegeben"));
    }

    @Test
    void missingEntity() {
        Map<String, String> v = values(STANDARDS);
        v.put("entity", "");
        assertThrows(IllegalArgumentException.class, () -> run("implementation", v, out));
    }
}
