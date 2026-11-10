package de.devtoolbox;

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
        assertTrue(Files.size(out.resolve("spezifikation-risiko.pdf")) > 0);
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
    void i5_specNotPdf(@TempDir Path tmp) throws Exception {
        Path md = tmp.resolve("spec.md");
        Files.writeString(md, "# keine PDF");
        Map<String, String> v = values(STANDARDS);
        v.put("spec", md.toString());
        Exception e = assertThrows(IllegalArgumentException.class, () -> run("implementation", v, out));
        assertTrue(e.getMessage().contains("Spezifikation ist keine PDF-Datei"), e.getMessage());
    }

    @Test
    void i7_targetAttributes() throws Exception {
        var r = run("implementation", values(STANDARDS), out);
        assertTrue(r.logText().contains("Zielattribute: 11 aus Risiko.json"), r.logText());
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
    void missingEntity() {
        Map<String, String> v = values(STANDARDS);
        v.put("entity", "");
        assertThrows(IllegalArgumentException.class, () -> run("implementation", v, out));
    }
}
