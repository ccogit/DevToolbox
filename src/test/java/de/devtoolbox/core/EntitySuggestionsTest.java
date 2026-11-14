package de.devtoolbox.core;

import de.devtoolbox.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntitySuggestionsTest {

    private String before;

    @BeforeEach
    void setUp(@TempDir Path projekte) {
        before = System.getProperty("devtoolbox.projekte");
        System.setProperty("devtoolbox.projekte", projekte.toString());
    }

    @AfterEach
    void tearDown() {
        if (before == null) {
            System.clearProperty("devtoolbox.projekte");
        } else {
            System.setProperty("devtoolbox.projekte", before);
        }
    }

    private static void project(String id, String json) throws Exception {
        Path folder = Files.createDirectories(Path.of(System.getProperty("devtoolbox.projekte")).resolve(id));
        Files.writeString(folder.resolve("projekt.json"), json);
    }

    private static EntitySuggestions.Entry entry(EntitySuggestions.Suggestion s, String name) {
        return s.entries().stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void targetModelOnlyIsTargetOnly() {
        var s = EntitySuggestions.suggest(null, TestSupport.ZIELMODELL, null, null);
        assertEquals(new EntitySuggestions.Entry("Risiko", false, true), entry(s, "Risiko"));
        assertEquals("nur Zielmodell", entry(s, "Vertrag").provenance());
    }

    @Test
    void mappingTemplatesCountAsTarget() {
        var s = EntitySuggestions.suggest(null, null, TestSupport.ZIEL.resolve("mapping-vorlagen"), null);
        assertTrue(entry(s, "Vertrag").target());
        assertFalse(entry(s, "Vertrag").legacy());
    }

    @Test
    void ddlOnlyIsLegacyOnly() {
        var s = EntitySuggestions.suggest(TestSupport.TESTPROJEKT.resolve("ddl"), null, null, null);
        assertFalse(s.isEmpty());
        assertTrue(s.entries().stream().allMatch(e -> e.legacy() && !e.target()), s.toString());
        assertTrue(s.names().contains("ALT_VERTRAG"), s.names().toString());
    }

    @Test
    void sourcesAreMergedByNormalisedName() {
        var s = EntitySuggestions.suggest(TestSupport.TESTPROJEKT.resolve("ddl"), TestSupport.ZIELMODELL,
                TestSupport.ZIEL.resolve("mapping-vorlagen"), TestSupport.TESTPROJEKT);
        // reference code (AltRisikoDto, ...) names Risiko and Vertrag too: both sides
        assertEquals(new EntitySuggestions.Entry("Risiko", true, true), entry(s, "Risiko"));
        assertEquals("Altdaten + Zielmodell", entry(s, "Vertrag").provenance());
        assertTrue(s.entries().stream().anyMatch(e -> e.legacy() && !e.target()), "legacy-only entries remain");
        assertEquals(s.names().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), s.names());
        assertEquals(s.names().size(), s.names().stream().map(String::toLowerCase).distinct().count());
    }

    @Test
    void referenceCodeWithoutPrefixesAndSuffixes() {
        var s = EntitySuggestions.suggest(null, null, null, TestSupport.TESTPROJEKT);
        assertTrue(s.names().contains("Risiko"), s.names().toString());
        assertTrue(s.names().stream().noneMatch(n -> n.startsWith("Alt") || n.startsWith("Neu")
                || n.endsWith("Dto") || n.endsWith("Entity")), s.names().toString());
    }

    @Test
    void nothingToDeriveFrom() {
        var s = EntitySuggestions.suggest(null, null, null, null);
        assertTrue(s.isEmpty());
    }

    @Test
    void storedListRoundTripAndChoices() throws Exception {
        project("x", "{\"name\":\"X\",\"entitaeten\":\"Risiko, NachlassZuschlag ,Risiko\"}");
        project("y", "{\"name\":\"Y\",\"zielmodell\":\"" + TestSupport.ZIELMODELL.toString().replace('\\', '/') + "\"}");
        MigrationProject x = MigrationProject.find("x").orElseThrow();
        assertEquals(List.of("Risiko", "NachlassZuschlag"), x.entities());
        assertEquals(x.entities(), x.entityChoices(), "no sources: only the stored extras");
        MigrationProject y = MigrationProject.find("y").orElseThrow();
        assertTrue(y.entities().isEmpty());
        assertTrue(y.entityChoices().contains("Vertrag"), "derived from the target model");
    }

    @Test
    void effectiveFromTargetModelWithAttributeCounts() {
        var e = EntitySuggestions.effective(TestSupport.TESTPROJEKT.resolve("ddl"), TestSupport.ZIELMODELL, null,
                TestSupport.TESTPROJEKT, List.of());
        assertTrue(e.fromTarget());
        var risiko = e.automatic().stream().filter(i -> i.name().equals("Risiko")).findFirst().orElseThrow();
        assertEquals("Zielmodell", risiko.origin());
        assertTrue(risiko.attributes() > 0, "attribute count of the target model");
        assertEquals(risiko.name() + " – " + risiko.attributes() + " Attribute (Zielmodell)", risiko.label());
        assertFalse(e.names().contains("ALT_VERTRAG"), "legacy sources are not mixed in: " + e.names());
    }

    @Test
    void effectiveWithoutTargetModelUsesDdlAndReferenceCode() {
        var e = EntitySuggestions.effective(TestSupport.TESTPROJEKT.resolve("ddl"), null, null,
                TestSupport.TESTPROJEKT, List.of());
        assertFalse(e.fromTarget());
        assertTrue(e.names().contains("ALT_VERTRAG"), e.names().toString());
        assertTrue(e.names().contains("Risiko"), e.names().toString());
        assertTrue(e.automatic().stream().allMatch(i -> i.attributes() == -1 && i.origin().equals("Altdaten")));
        assertEquals("ALT_VERTRAG – Altdaten", e.automatic().stream().filter(i -> i.name().equals("ALT_VERTRAG"))
                .findFirst().orElseThrow().label());
    }

    @Test
    void effectiveEmptyTargetModelFolderFallsBack(@TempDir Path empty) {
        var e = EntitySuggestions.effective(TestSupport.TESTPROJEKT.resolve("ddl"), empty, null, null, List.of());
        assertFalse(e.fromTarget());
        assertTrue(e.names().contains("ALT_VERTRAG"));
    }

    @Test
    void effectiveExtrasAreMergedWithoutDuplicates() {
        var e = EntitySuggestions.effective(null, TestSupport.ZIELMODELL, null, null,
                List.of("risiko", "Sonderfall", "SONDERFALL", " ", "Zusatz"));
        assertEquals(List.of("Sonderfall", "Zusatz"), e.extras(), "known and repeated names are dropped");
        assertEquals(1, e.names().stream().filter(n -> n.equalsIgnoreCase("Risiko")).count());
        assertTrue(e.names().containsAll(List.of("Sonderfall", "Zusatz")));
        assertEquals(List.of("Neu"), e.withoutAutomatic(List.of("Risiko", "Neu")), "stored extras are filtered");
    }

    @Test
    void effectiveNothingFound() {
        var e = EntitySuggestions.effective(null, null, null, null, List.of("Nur-Extra"));
        assertTrue(e.automatic().isEmpty());
        assertEquals(List.of("Nur-Extra"), e.names());
    }
    @Test
    void codeOnlyEntityCanBeSuggestedFromMigratedLogic(@TempDir Path tmp) throws Exception {
        Path src = Files.createDirectories(tmp.resolve("src/main/java/de/migration"));
        Files.writeString(src.resolve("NachlassZuschlagMapper.java"),
                "package de.migration; public class NachlassZuschlagMapper {}");
        Files.writeString(src.resolve("NachlassZuschlagWriter.java"),
                "package de.migration; public class NachlassZuschlagWriter {}");
        Path ignored = Files.createDirectories(tmp.resolve("target/classes"));
        Files.writeString(ignored.resolve("FalschTO.java"), "class FalschTO {}");
        assertEquals(List.of("NachlassZuschlag"), EntitySuggestions.fromCode(tmp));
    }

    @Test
    void configurationClassesAloneAreNoEntity(@TempDir Path tmp) throws Exception {
        Path src = Files.createDirectories(tmp.resolve("src/main/java/de/migration"));
        Files.writeString(src.resolve("BatchConfig.java"), "package de.migration; public class BatchConfig {}");
        Files.writeString(src.resolve("DataSourceConfiguration.java"),
                "package de.migration; public class DataSourceConfiguration {}");
        Files.writeString(src.resolve("RisikoJobConfiguration.java"),
                "package de.migration; public class RisikoJobConfiguration {}");
        Files.writeString(src.resolve("RisikoReader.java"), "package de.migration; public class RisikoReader {}");
        assertEquals(List.of("Risiko"), EntitySuggestions.fromCode(tmp));
    }

    @Test
    void recommendationPrefersEntitiesSupportedByBothSides() {
        var suggestion = new EntitySuggestions.Suggestion(List.of(
                new EntitySuggestions.Entry("Alt", true, false),
                new EntitySuggestions.Entry("NurZiel", false, true),
                new EntitySuggestions.Entry("Beide", true, true)));
        assertEquals("Beide", suggestion.best().orElseThrow().name());
        assertEquals(3, suggestion.best().orElseThrow().priority());
    }

}
