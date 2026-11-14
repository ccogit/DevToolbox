package de.devtoolbox.tools.standards;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaCatalogTest {
    private static Path source(Path root, String module, String pkg, String name, String body) throws Exception {
        Path file = root.resolve(module + "/src/main/java/" + pkg.replace('.', '/') + "/" + name + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "package " + pkg + ";\n" + body);
        return file;
    }

    @Test
    void separatesStockAndPendingVariantsWithoutInventingOtherEntities(@TempDir Path root) throws Exception {
        source(root, "read", "de.x.vertrag", "VertragTO",
                "public class VertragTO { String nummer; }");
        source(root, "read", "de.x.vertrag", "BestandVertragReader",
                "public class BestandVertragReader {}");
        source(root, "read", "de.x.vertrag", "SchwebeVertragReader",
                "public class SchwebeVertragReader {}");
        source(root, "process", "de.x.vertrag", "VertragMapperBestand",
                "public class VertragMapperBestand {}");
        source(root, "process", "de.x.vertrag", "VertragSchwebeProcessor",
                "public class VertragSchwebeProcessor {}");
        source(root, "write", "de.x.vertrag", "VertragItemWriter",
                "public class VertragItemWriter {}");
        source(root, "write", "de.x.common", "AbstractReader",
                "public abstract class AbstractReader { }");
        JavaCatalog index = JavaCatalog.scan(root);
        assertEquals(7, index.files().size());
        assertEquals(1, index.entities().size(), index.entities().toString());
        assertEquals(6, index.entities().get("vertrag").size());
        assertEquals("Schwebe", JavaCatalog.recognise(index.files().stream()
                .filter(f -> f.name().equals("SchwebeVertragReader")).findFirst().orElseThrow()).variant());
        assertEquals("Bestand", JavaCatalog.roleBeforeVariant(index.files().stream()
                .filter(f -> f.name().equals("VertragMapperBestand")).findFirst().orElseThrow()).variant());
    }

    @Test
    void explicitImportsBeatSameNamedTypesFromOtherModules(@TempDir Path root) throws Exception {
        source(root, "a", "de.one", "AbstractTO", "public abstract class AbstractTO {}");
        source(root, "b", "de.two", "AbstractTO", "public abstract class AbstractTO {}");
        source(root, "c", "de.use", "VertragTO", """
                import de.two.AbstractTO;
                public class VertragTO extends AbstractTO {}
                """);
        var catalog = JavaCatalog.scan(root);
        var child = catalog.files().stream().filter(t -> t.name().equals("VertragTO")).findFirst().orElseThrow();
        assertEquals(List.of("de.two.AbstractTO"),
                catalog.references(child).stream().map(JavaCatalog.Type::fqn).toList());
    }

    @Test
    void unresolvedDuplicateTypeIsNotGuessed(@TempDir Path root) throws Exception {
        source(root, "a", "de.x", "AbstractTO", "public abstract class AbstractTO {}");
        source(root, "b", "de.x", "AbstractTO", "public abstract class AbstractTO {}");
        source(root, "c", "de.x", "VertragTO", "public class VertragTO extends AbstractTO {}");
        var catalog = JavaCatalog.scan(root);
        assertEquals(2, catalog.duplicateTypes().size());
        var child = catalog.files().stream().filter(t -> t.name().equals("VertragTO")).findFirst().orElseThrow();
        assertEquals(2, catalog.resolve(child, "AbstractTO").size());
        assertTrue(catalog.references(child).isEmpty());
    }

    @Test
    void ignoresCommentsStringsAndNestedClassNames(@TempDir Path root) throws Exception {
        source(root, "module", "de.x", "VertragTO", """
                // extends AbstractFake and class PretendTO
                public class VertragTO {
                    String sql = "SELECT FROM AbstractFake";
                    String code = "OtherFakeTO";
                }
                """);
        source(root, "module", "de.x", "AbstractFake", "public abstract class AbstractFake {}");
        var catalog = JavaCatalog.scan(root);
        var model = catalog.files().stream().filter(t -> t.name().equals("VertragTO")).findFirst().orElseThrow();
        assertFalse(model.lexical().contains("AbstractFake"));
        assertTrue(catalog.references(model).isEmpty());
    }

    @Test
    void acceptsModelWithoutSuffixWhenJpaAnnotated(@TempDir Path root) throws Exception {
        source(root, "model", "de.x", "Produkt", "@Entity public class Produkt {}");
        var catalog = JavaCatalog.scan(root);
        assertEquals(1, catalog.entities().size());
        assertTrue(catalog.entities().containsKey("produkt"));
    }
}
