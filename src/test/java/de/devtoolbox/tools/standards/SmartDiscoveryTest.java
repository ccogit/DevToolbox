package de.devtoolbox.tools.standards;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SmartDiscoveryTest {
    private static void write(Path root, String module, String pkg, String name, String body) throws Exception {
        Path file = root.resolve(module + "/src/main/java/" + pkg.replace('.', '/') + "/" + name + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "package " + pkg + ";\n" + body);
    }

    @Test
    void includesStockSuspenseAndSharedHelpersButNotSubstringEntities(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("source");
        write(root, "model", "de.x.vertrag", "VertragTO",
                "import de.x.common.AbstractTO; public class VertragTO extends AbstractTO {}");
        write(root, "model", "de.x.vertrag", "VertragspartnerTO",
                "public class VertragspartnerTO {}");
        write(root, "reader", "de.x.vertrag.read", "BestandVertragReader",
                "public class BestandVertragReader {}");
        write(root, "reader", "de.x.vertrag.read", "SchwebeVertragReader",
                "public class SchwebeVertragReader {}");
        write(root, "process", "de.x.vertrag.mapping", "VertragMapperBestand",
                "import de.x.common.Rules; public class VertragMapperBestand { Rules r; }");
        write(root, "process", "de.x.vertrag.mapping", "VertragMapperSchwebe",
                "import de.x.common.Rules; public class VertragMapperSchwebe { Rules r; }");
        write(root, "writer", "de.x.vertrag.write", "VertragWriter",
                "public class VertragWriter {}");
        write(root, "common", "de.x.common", "AbstractTO",
                "public abstract class AbstractTO { public String id; }");
        write(root, "common", "de.x.common", "Rules",
                "public class Rules { public String rules; }");
        var preview = StandardsTool.preview(root, "Vertrag");
        assertEquals(6, preview.templates().size(), preview.templates().toString());
        assertTrue(preview.variants().contains("Bestand"), preview.toString());
        assertTrue(preview.variants().contains("Schwebe"), preview.toString());
        assertEquals(List.of("de.x.common.AbstractTO", "de.x.common.Rules"), preview.bases());
        assertTrue(preview.templates().stream().noneMatch(x -> x.contains("VertragspartnerTO")), preview.toString());
        Path output = tmp.resolve("standards");
        StandardsTool.Summary done = StandardsTool.generate(root, "Vertrag", output, "Test", s -> {});
        assertEquals(6, done.templates());
        assertEquals(2, done.baseTypes());
        assertTrue(Files.isRegularFile(output.resolve("basis/AbstractTO.java")));
        assertTrue(Files.isRegularFile(output.resolve("basis/Rules.java")));
        assertTrue(StandardsTool.load(output).rows().stream()
                .anyMatch(r -> r.notes().equals("Variante: Schwebe (automatisch erkannt)")));
    }

    @Test
    void detectsRelatedLogicWithoutTransferObjects(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("project");
        write(root, "reader", "de.x", "RisikoReader", "public class RisikoReader {}");
        write(root, "mapper", "de.x", "RisikoMapper", "public class RisikoMapper {}");
        write(root, "writer", "de.x", "RisikoWriter", "public class RisikoWriter {}");
        var preview = StandardsTool.preview(root, "");
        assertEquals("Risiko", preview.entity());
        assertEquals(3, preview.templates().size());
    }

    @Test
    void qualifiedDependenciesDoNotChooseRandomSameNamedBase(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("source");
        write(root, "one", "de.one", "AbstractTO", "public abstract class AbstractTO {}");
        write(root, "two", "de.two", "AbstractTO", "public abstract class AbstractTO {}");
        write(root, "model", "de.x", "RisikoTO",
                "import de.two.AbstractTO; public class RisikoTO extends AbstractTO {}");
        var preview = StandardsTool.preview(root, "Risiko");
        assertEquals(List.of("de.two.AbstractTO"), preview.bases());
    }

    @Test
    void picksUpVariantFromPackageOrSpringQualifier(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("source");
        write(root, "model", "de.x.risiko", "RisikoTO", "public class RisikoTO {}");
        write(root, "process", "de.x.risiko.schwebe", "RisikoMapper",
                "public class RisikoMapper {}");
        write(root, "write", "de.x.risiko", "RisikoWriter",
                "@Qualifier(\"bestand\") public class RisikoWriter {}");
        var preview = StandardsTool.preview(root, "Risiko");
        assertTrue(preview.variants().stream().anyMatch(v -> v.equalsIgnoreCase("Schwebe")), preview.toString());
        assertTrue(preview.variants().stream().anyMatch(v -> v.equalsIgnoreCase("Bestand")), preview.toString());
        Path output = tmp.resolve("standards");
        StandardsTool.generate(root, "Risiko", output, "Test", x -> {});
        var rows = StandardsTool.load(output).rows();
        assertTrue(rows.stream().anyMatch(row -> row.className().contains("Mapper")
                && row.notes().contains("Schwebe")), rows.toString());
        assertTrue(rows.stream().anyMatch(row -> row.className().contains("Writer")
                && row.notes().contains("Bestand")), rows.toString());
    }

    @Test
    void previewNeverWritesAndIsStable(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("project");
        write(root, "read", "de.x", "RisikoTO", "public class RisikoTO {}");
        Path output = tmp.resolve("standards");
        String before = StandardsTool.preview(root, "").toString();
        assertEquals(before, StandardsTool.preview(root, "").toString());
        assertFalse(Files.exists(output));
    }
}
