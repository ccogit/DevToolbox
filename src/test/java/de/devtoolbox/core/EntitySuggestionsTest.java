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
        var s = EntitySuggestions.suggest(null, TestSupport.ZIELMODELL, null, null, null);
        assertEquals(new EntitySuggestions.Entry("Risiko", false, true), entry(s, "Risiko"));
        assertEquals("nur Zielmodell", entry(s, "Vertrag").provenance());
    }

    @Test
    void mappingTemplatesCountAsTarget() {
        var s = EntitySuggestions.suggest(null, null, TestSupport.ZIEL.resolve("mapping-vorlagen"), null, null);
        assertTrue(entry(s, "Vertrag").target());
        assertFalse(entry(s, "Vertrag").legacy());
    }

    @Test
    void ddlOnlyIsLegacyOnly() {
        var s = EntitySuggestions.suggest(TestSupport.TESTPROJEKT.resolve("ddl"), null, null, null, null);
        assertFalse(s.isEmpty());
        assertTrue(s.entries().stream().allMatch(e -> e.legacy() && !e.target()), s.toString());
        assertTrue(s.names().contains("ALT_VERTRAG"), s.names().toString());
    }

    @Test
    void sourcesAreMergedByNormalisedName() {
        var s = EntitySuggestions.suggest(TestSupport.TESTPROJEKT.resolve("ddl"), TestSupport.ZIELMODELL,
                TestSupport.ZIEL.resolve("mapping-vorlagen"), TestSupport.TESTPROJEKT, null);
        // reference code (AltRisikoDto, ...) names Risiko and Vertrag too: both sides
        assertEquals(new EntitySuggestions.Entry("Risiko", true, true), entry(s, "Risiko"));
        assertEquals("Altdaten + Zielmodell", entry(s, "Vertrag").provenance());
        assertTrue(s.entries().stream().anyMatch(e -> e.legacy() && !e.target()), "legacy-only entries remain");
        assertEquals(s.names().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), s.names());
        assertEquals(s.names().size(), s.names().stream().map(String::toLowerCase).distinct().count());
    }

    @Test
    void referenceCodeWithoutPrefixesAndSuffixes() {
        var s = EntitySuggestions.suggest(null, null, null, TestSupport.TESTPROJEKT, null);
        assertTrue(s.names().contains("Risiko"), s.names().toString());
        assertTrue(s.names().stream().noneMatch(n -> n.startsWith("Alt") || n.startsWith("Neu")
                || n.endsWith("Dto") || n.endsWith("Entity")), s.names().toString());
    }

    @Test
    void otherProjectsWhenNothingElseIsGiven() throws Exception {
        project("a", "{\"name\":\"A\",\"entitaeten\":\"Zeta, Alpha\"}");
        project("b", "{\"name\":\"B\",\"entitaeten\":\"Alpha, Beta\"}");
        project("c", "{\"name\":\"C\",\"entitaeten\":\"Nur-C\"}");
        var s = EntitySuggestions.suggest(null, null, null, null, "c");
        assertEquals("aus anderen Projekten", s.entries().get(0).provenance());
        assertEquals(List.of("Alpha", "Beta", "Zeta"), s.names());
    }

    @Test
    void nothingToDeriveFrom() {
        var s = EntitySuggestions.suggest(null, null, null, null, null);
        assertTrue(s.isEmpty());
    }

    @Test
    void storedListRoundTripAndChoices() throws Exception {
        project("x", "{\"name\":\"X\",\"entitaeten\":\"Risiko, NachlassZuschlag ,Risiko\"}");
        project("y", "{\"name\":\"Y\",\"zielmodell\":\"" + TestSupport.ZIELMODELL.toString().replace('\\', '/') + "\"}");
        MigrationProject x = MigrationProject.find("x").orElseThrow();
        assertEquals(List.of("Risiko", "NachlassZuschlag"), x.entities());
        assertEquals(x.entities(), x.entityChoices());
        MigrationProject y = MigrationProject.find("y").orElseThrow();
        assertTrue(y.entities().isEmpty());
        assertTrue(y.entityChoices().contains("Vertrag"), "falls back to the target model");
    }
}
