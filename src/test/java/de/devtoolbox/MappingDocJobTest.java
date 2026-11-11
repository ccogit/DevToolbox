package de.devtoolbox;

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

class MappingDocJobTest {

    @TempDir
    Path out;

    /** With the values of the migration project TESTZIEL (root, mapping templates). */
    @Test
    void projectValues() throws Exception {
        Map<String, String> v = new HashMap<>(MigrationProject.find("testziel").orElseThrow().values());
        v.put("terms", "Vertrag");
        var r = run("mapping-doc", v, out);
        String log = r.logText();
        assertEquals(List.of("skill-mapping-dokumentation.md", "aufgabe-mapping-dokumentation-vertrag.md",
                "mapping-vorlage-vertrag.txt"), r.names(), log);
        assertTrue(log.contains("Mapping-Vorlage: Vertrag.txt – Vertrag, 4 Attribute"), log);
        assertTrue(log.contains("Voranalyse: 3 von 4 Attributen mit Fundstelle, 0 mit Schwebe-Bezug, 0 Schwebe-Stellen"), log);
        assertTrue(log.contains("Ergebnis von Copilot: mapping-vertrag.txt (Download)"), log);

        assertEquals(Files.readString(ZIEL.resolve("mapping-vorlagen/Vertrag.txt")), r.read("mapping-vorlage-vertrag.txt"),
                "template is handed over unchanged");
        String task = r.read("aufgabe-mapping-dokumentation-vertrag.md");
        assertTrue(task.contains("# Aufgabe: Mapping-Dokumentation „Vertrag“"));
        assertTrue(task.contains("Datei `mapping-vertrag.txt` zum Download"));
        assertTrue(task.contains("Spalte **„Mapping“** (Bestand) und die Spalte **„Schwebe Mapping“** (Schwebe)"));
        assertTrue(task.contains("| 1 | vertragsnummer | `VertragMapper.mappe:"), task);
        assertTrue(task.contains("| 4 | kuendigungsgrund | – keine gefunden |"), task);
        assertTrue(task.contains("Keine Zeile der Process-Phase erwähnt „Schwebe“"));
        assertTrue(task.contains("===== BEGIN FILE: src/main/java/de/vers/neu/vertrag/mapping/VertragMapper.java ====="), task);
        assertTrue(task.contains("Migrationsprojekt (durchsucht)"));
        assertFalse(task.contains("VertragWriter.java ====="), "Write phase is not part of the mapping code");
        assertFalse(task.contains("{{"), "all placeholders filled");
        assertTrue(r.read("skill-mapping-dokumentation.md").contains("Bestand und Schwebe"));
    }

    @Test
    void schwebeSpecialPath(@TempDir Path project) throws Exception {
        Path pkg = project.resolve("src/main/java/de/x/zuschlag");
        Files.createDirectories(pkg.resolve("mapping"));
        Files.writeString(pkg.resolve("mapping/ZuschlagProcessor.java"), """
                package de.x.zuschlag.mapping;

                public class ZuschlagProcessor implements org.springframework.batch.item.ItemProcessor<AltZuschlagTO, ZuschlagEntity> {
                    public ZuschlagEntity process(AltZuschlagTO to) {
                        ZuschlagEntity e = new ZuschlagEntity();
                        e.setBetrag(to.getBetrag());
                        if (to.isSchwebe()) {
                            e.setStatus("S");
                        }
                        return e;
                    }
                }
                """);
        Files.writeString(pkg.resolve("ZuschlagEntity.java"), """
                package de.x.zuschlag;
                public class ZuschlagEntity { private String betrag; private String status; }
                """);
        Path vorlagen = Files.createDirectories(project.resolve("vorlagen"));
        Files.writeString(vorlagen.resolve("z.md"), """
                ||Entität||Attribut||Mapping||Schwebe Mapping||Status||
                |Zuschlag|Betrag|TBD|TBD|TBD|
                | |Status|TBD|TBD|TBD|
                """);
        Map<String, String> v = new HashMap<>();
        v.put("target", project.toString());
        v.put("terms", "Zuschlag");
        v.put("mappingTemplates", vorlagen.toString());
        var r = run("mapping-doc", v, out);
        assertEquals(List.of("skill-mapping-dokumentation.md", "aufgabe-mapping-dokumentation-zuschlag.md",
                "mapping-vorlage-zuschlag.md"), r.names(), r.logText());
        assertTrue(r.logText().contains("Voranalyse: 2 von 2 Attributen mit Fundstelle, 1 mit Schwebe-Bezug, 1 Schwebe-Stellen"),
                r.logText());
        String task = r.read("aufgabe-mapping-dokumentation-zuschlag.md");
        assertTrue(task.contains("| 2 | Status | `ZuschlagProcessor.process:8` | ja (nur im Schwebe-Kontext) |"), task);
        assertTrue(task.contains("| `ZuschlagProcessor.process:7` | `if (to.isSchwebe()) {` |"), task);
    }

    @Test
    void missingTemplateFolderOrTemplate() {
        Map<String, String> v = new HashMap<>();
        v.put("target", ZIEL.toString());
        v.put("terms", "Vertrag");
        var e = assertThrows(IllegalArgumentException.class, () -> run("mapping-doc", v, out));
        assertTrue(e.getMessage().contains("Mapping-Vorlagen"), e.getMessage());

        v.put("mappingTemplates", ZIEL.resolve("mapping-vorlagen").toString());
        v.put("terms", "Risiko");
        e = assertThrows(IllegalArgumentException.class, () -> run("mapping-doc", v, out));
        assertTrue(e.getMessage().startsWith("Keine Mapping-Vorlage für Risiko"), e.getMessage());
    }
}
