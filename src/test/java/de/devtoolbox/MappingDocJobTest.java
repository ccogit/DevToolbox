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

    private static final String FERTIG = "{status:colour=Green|title=FERTIG|subtle=true}";

    @TempDir
    Path out;

    /** Neutral Jira projects, independent of the local settings file of the machine running the tests. */
    private final java.util.Map<String, String> savedJira = new java.util.HashMap<>();

    @org.junit.jupiter.api.BeforeEach
    void neutralJiraProjects() {
        for (String key : java.util.List.of(de.devtoolbox.core.ProjectLayout.JIRA_LIABILITY_PROPERTY,
                de.devtoolbox.core.ProjectLayout.JIRA_PROPERTY_PROPERTY)) {
            savedJira.put(key, System.getProperty(key));
        }
        System.setProperty(de.devtoolbox.core.ProjectLayout.JIRA_LIABILITY_PROPERTY, "HAFT");
        System.setProperty(de.devtoolbox.core.ProjectLayout.JIRA_PROPERTY_PROPERTY, "SACH");
    }

    @org.junit.jupiter.api.AfterEach
    void restoreJiraProjects() {
        savedJira.forEach((key, value) -> {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        });
    }

    /** With the values of the migration project TESTZIEL (root, target model, mapping templates). */
    @Test
    void projectValues() throws Exception {
        Map<String, String> v = new HashMap<>(MigrationProject.find("testziel").orElseThrow().values());
        v.put("terms", "Vertrag");
        v.put("jira", "sach-4711");
        var r = run("mapping-doc", v, out);
        String log = r.logText();
        assertEquals(List.of("skill-mapping-dokumentation.md", "aufgabe-mapping-dokumentation-vertrag.md",
                "vorlagen-mapping-vertrag.md"), r.names(), log);
        assertTrue(log.contains("Zielmodell-JSON: Vertrag.json – Vertrag, 3 Attribute"), log);
        assertTrue(log.contains("Mapping-Vorlage: Vertrag.txt – Vertrag, 4 Attribute"), log);
        assertTrue(log.contains("WARNUNG: Attribute von JSON und Vorlage weichen ab"), log);
        assertTrue(log.contains("Voranalyse: 3 von 3 Attributen mit Fundstelle"), log);
        assertTrue(log.contains("Ergebnis von Copilot (Download): Vertrag.json, Vertrag.txt"), log);
        assertFalse(log.contains("WARNUNG: Jira"), "SACH fits Sachversicherung");
        // C1/E3: size report of the three files; C2: the default limit applies
        for (String file : r.names()) {
            assertTrue(log.contains("Größe " + file + ": "), log);
        }
        assertTrue(log.contains("Größe gesamt: "), log);
        assertTrue(log.contains("Copilot-Limit 30.000 – ok") || log.contains("Copilot-Limit 30,000 – ok"), log);
        // E2: the prompt asks to report an incomplete result
        assertTrue(log.contains("melde Status: UNVOLLSTÄNDIG und was fehlt."), log);
        assertTrue(r.read("skill-mapping-dokumentation.md").contains("**Unvollständigkeit melden.**"));

        String docs = r.read("vorlagen-mapping-vertrag.md");
        assertTrue(docs.contains("===== BEGIN FILE: Vertrag.json ====="));
        assertTrue(docs.contains("===== BEGIN FILE: Vertrag.txt ====="));
        assertTrue(docs.contains("|Vertrag|vertragsnummer|{status:colour=Red|title=TBD|subtle=true}|"
                + "{status:colour=Red|title=TBD|subtle=true}|{jiraissues:SACH-4711}|" + FERTIG + "|"), docs);
        assertTrue(docs.contains("| |kuendigungsgrund|"), docs);

        String task = r.read("aufgabe-mapping-dokumentation-vertrag.md");
        assertTrue(task.contains("# Aufgabe: Mapping-Dokumentation „Vertrag“"));
        assertTrue(task.contains("Stelle `Vertrag.json` und `Vertrag.txt` als **Dateien zum Download** bereit"), task);
        assertTrue(task.contains("Spezifikation (`{jiraissues:SACH-4711}`)"), task);
        assertTrue(task.contains("| 1 | vertragsnummer | `VertragMapper.mappe:"), task);
        assertTrue(task.contains("===== BEGIN FILE: src/main/java/de/vers/neu/vertrag/mapping/VertragMapper.java ====="), task);
        assertTrue(task.contains("VertragPoRowMapper.java ====="), "Read phase gives the source columns");
        assertFalse(task.contains("VertragWriter.java ====="), "Write phase is not part of the mapping code");
        assertFalse(task.contains("{{"), "all placeholders filled");
        assertTrue(r.read("skill-mapping-dokumentation.md").contains("Wenn …, dann …, ansonsten …"));
    }

    /**
     * The real structure: the migration is a module of the line's parent project; target model and templates lie in
     * {@code <parent>-designer-services} and are found without configuration. Ticket number without prefix.
     */
    @Test
    void derivedFromProjectLayout(@TempDir Path tmp) throws Exception {
        Path parent = tmp.resolve("firma-haftpflichtvertrag");
        Path root = parent.resolve("firma-haftpflichtvertrag-migration");
        Path designer = parent.resolve("firma-haftpflichtvertrag-designer-services");
        Path pkg = Files.createDirectories(root.resolve("src/main/java/de/x/zuschlag/mapping"));
        Files.writeString(pkg.resolve("NachlassZuschlagProcessor.java"), """
                package de.x.zuschlag.mapping;

                public class NachlassZuschlagProcessor implements org.springframework.batch.item.ItemProcessor<AltTO, NachlassZuschlag> {
                    public NachlassZuschlag process(AltTO to) {
                        NachlassZuschlag e = new NachlassZuschlag();
                        e.setNachlassZuschlagArt(to.getArt());
                        if (to.isSchwebe()) {
                            e.setDiffStatus("S");
                        }
                        return e;
                    }
                }
                """);
        Path properties = Files.createDirectories(designer.resolve("doc/migration/properties"));
        Files.writeString(properties.resolve("application.json"), "{\"irgendwas\": 1}");
        Files.writeString(properties.resolve("NachlassZuschlag.json"), """
                {
                "entityTypeName" : "NachlassZuschlag",
                "typeAttributeDocumentationList" : [ {
                "attributeName" : "DiffStatus",
                "mapping" : "{status:colour=Red|title=TBD|subtle=true}",
                "schwebeMapping" : "{status:colour=Red|title=TBD|subtle=true}",
                "specification" : [ "{status:colour=Red|title=JIRA|subtle=true}" ],
                "status" : [ "{status:colour=Red|title=TBD|subtle=true}" ]
                }, {
                "attributeName" : "NachlassZuschlagArt",
                "mapping" : "{status:colour=Red|title=TBD|subtle=true}",
                "schwebeMapping" : "{status:colour=Red|title=TBD|subtle=true}",
                "specification" : [ "{status:colour=Red|title=JIRA|subtle=true}" ],
                "status" : [ "{status:colour=Red|title=TBD|subtle=true}" ]
                } ]
                }
                """);
        Path documentation = Files.createDirectories(designer.resolve("resources/documentation"));
        Files.writeString(documentation.resolve("NachlassZuschlag.confluence"), """
                ||Entität||Attribut||Mapping||Schwebe Mapping||Spezifikation||Status||
                |NachlassZuschlag|DiffStatus|{status:colour=Red|title=TBD|subtle=true}|{status:colour=Red|title=TBD|subtle=true}|{status:colour=Red|title=JIRA|subtle=true}|{status:colour=Red|title=TBD|subtle=true}|
                | |NachlassZuschlagArt|{status:colour=Red|title=TBD|subtle=true}|{status:colour=Red|title=TBD|subtle=true}|{status:colour=Red|title=JIRA|subtle=true}|{status:colour=Red|title=TBD|subtle=true}|
                """);

        Map<String, String> v = new HashMap<>();
        v.put("target", root.toString());
        v.put("terms", "NachlassZuschlag");
        v.put("jira", "6554");
        var r = run("mapping-doc", v, out);
        String log = r.logText();
        assertTrue(log.contains("Zielmodell: " + properties + " (aus dem Migrationsmodul abgeleitet)"), log);
        assertTrue(log.contains("Mapping-Vorlagen: " + documentation + " (aus dem Migrationsmodul abgeleitet)"), log);
        assertTrue(log.contains("Spezifikation: {jiraissues:HAFT-6554}"), log);
        assertTrue(log.contains("Voranalyse: 2 von 2 Attributen mit Fundstelle, 1 mit Schwebe-Bezug, 1 Schwebe-Stellen"), log);
        assertTrue(log.contains("Ergebnis von Copilot (Download): NachlassZuschlag.json, NachlassZuschlag.txt"), log);

        String docs = r.read("vorlagen-mapping-nachlasszuschlag.md");
        assertEquals(2, count(docs, "\"specification\" : [ \"{jiraissues:HAFT-6554}\" ]"), docs);
        assertEquals(2, count(docs, "\"status\" : [ \"" + FERTIG + "\" ]"), docs);
        assertEquals(4, count(docs, "\"mapping\" : \"{status:colour=Red|title=TBD|subtle=true}\"")
                + count(docs, "\"schwebeMapping\" : \"{status:colour=Red|title=TBD|subtle=true}\""), "mappings stay for Copilot");
        assertEquals(2, count(docs, "|{jiraissues:HAFT-6554}|" + FERTIG + "|"), docs);
        String task = r.read("aufgabe-mapping-dokumentation-nachlasszuschlag.md");
        assertTrue(task.contains("| 1 | DiffStatus | `NachlassZuschlagProcessor.process:8` | ja (nur im Schwebe-Kontext) |"), task);
        assertTrue(task.contains("| `NachlassZuschlagProcessor.process:7` | `if (to.isSchwebe()) {` |"), task);

        assertFalse(task.contains("REFERENZ/"), "no reference given");

        // a wrong prefix is accepted but reported
        v.put("jira", "SACH-1");
        assertTrue(run("mapping-doc", v, out).logText().contains("WARNUNG: Jira-Ticket SACH-1"));
    }

    /** The reference project is only compared – its code is a separate, marked section after the migration's code. */
    @Test
    void referenceOnlyForComparison(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("migration/src/main/java/de/x/mapping"));
        Files.writeString(root.resolve("ZuschlagMapper.java"),
                "package de.x.mapping;\npublic class ZuschlagMapper {\n    void mappe(A a, Z z) {\n        z.setArt(a.getArt());\n    }\n}\n");
        Path ref = Files.createDirectories(tmp.resolve("referenz/src/main/java/de/x/mapping"));
        Files.writeString(ref.resolve("ZuschlagMapper.java"),
                "package de.x.mapping;\npublic class ZuschlagMapper {\n    void mappe(A a, Z z) {\n        z.setArt(\"FEST\");\n    }\n}\n");
        Path vorlagen = Files.createDirectories(tmp.resolve("vorlagen"));
        Files.writeString(vorlagen.resolve("Zuschlag.txt"), "||Entität||Attribut||Mapping||Schwebe Mapping||\n|Zuschlag|Art|TBD|TBD|\n");
        Map<String, String> v = new HashMap<>();
        v.put("target", tmp.resolve("migration").toString());
        v.put("base", tmp.resolve("referenz").toString());
        v.put("terms", "Zuschlag");
        v.put("jira", "HAFT-1");
        v.put("mappingTemplates", vorlagen.toString());
        var r = run("mapping-doc", v, out);
        assertTrue(r.logText().contains("Referenzprojekt referenz: 1 Dateien der Process-Phase zum Abgleich"), r.logText());
        String task = r.read("aufgabe-mapping-dokumentation-zuschlag.md");
        int migration = task.indexOf("===== BEGIN FILE: src/main/java/de/x/mapping/ZuschlagMapper.java =====");
        int reference = task.indexOf("===== BEGIN FILE: REFERENZ/src/main/java/de/x/mapping/ZuschlagMapper.java =====");
        assertTrue(migration > 0 && reference > migration, task);
        assertTrue(task.contains("# Referenzprojekt referenz – nur zum Vergleich"));
        assertTrue(task.contains("**Abgleich:**"));
    }

    @Test
    void missingTicketTemplateOrJson() {
        Map<String, String> v = new HashMap<>();
        v.put("target", ZIEL.toString());
        v.put("terms", "Vertrag");
        var e = assertThrows(IllegalArgumentException.class, () -> run("mapping-doc", v, out));
        assertTrue(e.getMessage().startsWith("Das Jira-Ticket der Spezifikation fehlt"), e.getMessage());

        v.put("jira", "4711");
        e = assertThrows(IllegalArgumentException.class, () -> run("mapping-doc", v, out));
        assertTrue(e.getMessage().contains("ohne Projekt"), "no prefix derivable: " + e.getMessage());

        v.put("jira", "HAFT-4711");
        e = assertThrows(IllegalArgumentException.class, () -> run("mapping-doc", v, out));
        assertTrue(e.getMessage().startsWith("Weder Zielmodell-JSON noch Mapping-Vorlage für Vertrag"), e.getMessage());

        v.put("mappingTemplates", ZIEL.resolve("gibtsnicht").toString());
        e = assertThrows(IllegalArgumentException.class, () -> run("mapping-doc", v, out));
        assertTrue(e.getMessage().startsWith("Mapping-Vorlagen-Ordner nicht gefunden"), e.getMessage());
    }
    @Test
    void mixedTemplatePrefillsAndAnalysesOnlyRequestedEntity(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("migration/src/main/java/de/x/mapping"));
        Files.writeString(root.resolve("RisikoMapper.java"),
                "package de.x.mapping;\npublic class RisikoMapper { void mappe(Z z) { z.setFeld(1); } }\n");
        Path templates = Files.createDirectories(tmp.resolve("vorlagen"));
        Files.writeString(templates.resolve("mapping.txt"), """
                ||Entität||Attribut||Mapping||Schwebe Mapping||Spezifikation||Status||
                |Risiko|Feld|a|b|alt|offen|
                |Vertrag|Nummer|c|d|behalten|offen|
                | |Tarif|e|f|behalten|offen|
                """);
        Map<String, String> values = new HashMap<>();
        values.put("target", tmp.resolve("migration").toString());
        values.put("terms", "Risiko");
        values.put("jira", "HAFT-7");
        values.put("mappingTemplates", templates.toString());
        var result = run("mapping-doc", values, tmp.resolve("ausgabe"));
        String documents = result.read("vorlagen-mapping-risiko.md");
        assertTrue(documents.contains("|Risiko|Feld|a|b|{jiraissues:HAFT-7}|"
                + FERTIG + "|"), documents);
        assertTrue(documents.contains("|Vertrag|Nummer|c|d|behalten|offen|"), documents);
        assertTrue(documents.contains("| |Tarif|e|f|behalten|offen|"), documents);
        String task = result.read("aufgabe-mapping-dokumentation-risiko.md");
        assertTrue(task.contains("| 1 | Feld |"), task);
        assertFalse(task.contains("| 2 | Nummer |"), task);
        assertFalse(task.contains("| 3 | Tarif |"), task);
    }

}
