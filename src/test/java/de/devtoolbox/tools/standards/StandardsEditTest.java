package de.devtoolbox.tools.standards;

import de.devtoolbox.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Adding and removing templates and base classes, checked by reading the standard back with load(). */
class StandardsEditTest {

    private static final String CLASS = """
            package de.vers.neu.vertrag.to;

            @Deprecated
            public class VertragTO extends AbstractTO {
                String vertragsnummer;
            }
            """;

    @Test
    void addAndRemoveTemplateOnExistingStandard(@TempDir Path tmp) throws Exception {
        Path std = tmp.resolve("standards");
        TestSupport.copyTree(TestSupport.PROJEKTE.resolve("testziel/standards"), std);
        int before = StandardsTool.load(std).rows().size();
        String example = StandardsTool.load(std).example();

        Path file = tmp.resolve(example + "Extra.java");
        Files.writeString(file, CLASS.replace("Vertrag", example).replace("vertrag", example.toLowerCase()));
        StandardsTool.Row added = StandardsTool.addTemplate(std, file, "", "Read", "Transferobjekt (TO)");

        StandardsTool.Stored s = StandardsTool.load(std);
        assertEquals(before + 1, s.rows().size());
        StandardsTool.Row row = s.rows().get(before);
        assertEquals("Read", row.phase());
        assertEquals("{{Entitaet}}Extra", row.className());
        assertEquals("vorlagen/{{Entitaet}}Extra.java", row.template());
        assertTrue(row.pkg().contains("{{entitaet}}"), row.pkg());
        assertEquals(added.template(), row.template());
        String template = Files.readString(std.resolve(row.template()));
        assertTrue(template.contains("class {{Entitaet}}TO"), template);
        assertEquals(before + 1, s.rows().size());

        StandardsTool.removeRow(std, row);
        assertEquals(before, StandardsTool.load(std).rows().size());
        assertFalse(Files.exists(std.resolve(row.template())));
    }

    @Test
    void addAndRemoveBaseClassAndEntityFromAskedName(@TempDir Path tmp) throws Exception {
        Path std = tmp.resolve("neu");
        Path to = tmp.resolve("VertragTO.java");
        Files.writeString(to, CLASS);
        StandardsTool.Row row = StandardsTool.addTemplate(std, to, "Vertrag", "Read", "Transferobjekt (TO)");
        assertEquals("{{Entitaet}}TO", row.className());

        Path base = tmp.resolve("AbstractTO.java");
        Files.writeString(base, "package de.vers;\npublic abstract class AbstractTO {}\n");
        Path copy = StandardsTool.addBase(std, base);
        assertEquals(Files.readString(base), Files.readString(copy), "unchanged");

        StandardsTool.Stored s = StandardsTool.load(std);
        assertEquals(1, s.rows().size());
        assertEquals("AbstractTO", s.rows().get(0).supers());
        assertEquals(1, s.baseFiles().size());
        assertTrue(Files.readString(std.resolve(StandardsTool.INDEX)).contains("basis/AbstractTO.java"));

        StandardsTool.removeBase(std, s.baseFiles().get(0));
        assertTrue(StandardsTool.load(std).baseFiles().isEmpty());
        assertFalse(Files.readString(std.resolve(StandardsTool.INDEX)).contains("- `basis/AbstractTO.java`"));
        StandardsTool.removeRow(std, row);
        assertTrue(StandardsTool.load(std).rows().isEmpty());
    }
}
