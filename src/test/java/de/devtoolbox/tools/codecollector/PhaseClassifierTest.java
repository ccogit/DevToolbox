package de.devtoolbox.tools.codecollector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhaseClassifierTest {

    private static final String P = "src/main/java/de/x/";

    @Test
    void repositoryRule() {
        assertTrue(PhaseClassifier.isRepository("NeuRisikoRepository"));
        assertFalse(PhaseClassifier.isRepository("RisikoRepo"));
        assertFalse(PhaseClassifier.isRepository("RepositoryHelper"));
    }

    @Test
    void repositoryIsReadUnlessSomethingSaysOtherwise() {
        assertEquals(Phase.READ, PhaseClassifier.classify(P + "a/FooRepository.java", "public interface FooRepository { }"));
        assertEquals(Phase.READ, PhaseClassifier.classify(P + "repository/FooRepository.java",
                "@Configuration class FooRepository { }"), "no batch phase from the content: Read");
        // directory, implemented type and package place a repository in another phase
        assertEquals(Phase.WRITE, PhaseClassifier.classify(P + "write/NeuRisikoRepository.java",
                "public interface NeuRisikoRepository { }"));
        assertEquals(Phase.PROCESS, PhaseClassifier.classify(P + "mapping/FooRepository.java", "class FooRepository { }"));
        assertEquals(Phase.WRITE, PhaseClassifier.classify(P + "a/FooRepository.java",
                "class FooRepository implements ItemWriter<Foo> { }"));
        assertEquals(Phase.PROCESS, PhaseClassifier.classify(P + "process/FooRepository.java", "class FooRepository { }"));
    }

    @Test
    void enrichmentIsRead() {
        assertEquals(Phase.READ, PhaseClassifier.classify(P + "a/RisikoAnreicherer.java",
                "class RisikoAnreicherer implements ItemProcessor<A, B> { }"));
        assertEquals(Phase.READ, PhaseClassifier.classify(P + "a/VertragAnreicherungService.java",
                "class VertragAnreicherungService { }"));
        assertEquals(Phase.READ, PhaseClassifier.classify(P + "process/DeckungsAnreichererImpl.java",
                "class DeckungsAnreichererImpl { }"));
        assertEquals(Phase.READ, PhaseClassifier.classify(P + "risikoanreicherung/Foo.java", "class Foo { }"));
        // a fixed directory still wins
        assertEquals(Phase.PROCESS, PhaseClassifier.classify(P + "mapper/FooAnreicherer.java", "class FooAnreicherer { }"));
    }

    @Test
    void directoryRulesBeatInterfacesAndNames() {
        assertEquals(Phase.READ, PhaseClassifier.classify(P + "anreicherung/RisikoDeckungsAnreicherer.java",
                "class RisikoDeckungsAnreicherer implements ItemProcessor<A, B> { }"));
        assertEquals(Phase.PROCESS, PhaseClassifier.classify(P + "mapping/RisikoSchluesselDao.java",
                "class RisikoSchluesselDao { }"));
        assertEquals(Phase.WRITE, PhaseClassifier.classify(P + "writer/RisikoExportConfig.java",
                "@Configuration class RisikoExportConfig { }"));
        assertEquals(Phase.READ, PhaseClassifier.classify(P + "Anreicherer/Foo.java", "class Foo { }"), "case-insensitive");
        assertEquals(Phase.PROCESS, PhaseClassifier.classify("a\\mapper\\Foo.java", "class Foo { }"), "backslashes");
    }

    @Test
    void innermostDirectoryWins() {
        assertEquals(Phase.WRITE, PhaseClassifier.classify(P + "mapper/writer/Foo.java", "class Foo { }"));
        assertEquals(Phase.PROCESS, PhaseClassifier.classify(P + "writer/mapper/Foo.java", "class Foo { }"));
    }

    @Test
    void interfacesAndNames() {
        assertEquals(Phase.READ, PhaseClassifier.classify(P + "a/Foo.java", "class Foo implements ItemReader<X> { }"));
        assertEquals(Phase.READ, PhaseClassifier.classify(P + "a/Foo.java", "class Foo implements RowMapper<X> { }"));
        assertEquals(Phase.PROCESS, PhaseClassifier.classify(P + "a/Foo.java", "class Foo implements ItemProcessor<X, Y> { }"));
        assertEquals(Phase.WRITE, PhaseClassifier.classify(P + "a/Foo.java", "class Foo implements ItemWriter<X> { }"));
        assertEquals(Phase.PROCESS, PhaseClassifier.classify(P + "a/BeitragsConverter.java", "class BeitragsConverter { }"));
        assertEquals(Phase.WRITE, PhaseClassifier.classify(P + "a/FehlerProtokollWriter.java", "class FehlerProtokollWriter { }"));
        assertEquals(Phase.PERSISTENCE, PhaseClassifier.classify(P + "a/AltBestandDao.java", "class AltBestandDao { }"));
        assertEquals(Phase.CONFIG, PhaseClassifier.classify(P + "a/FooJobConfig.java", "class FooJobConfig { }"));
        assertEquals(Phase.MODEL, PhaseClassifier.classify(P + "a/FooDto.java", "class FooDto { }"));
        assertEquals(Phase.MODEL, PhaseClassifier.classify(P + "a/Foo.java", "@Entity class Foo { }"));
        assertEquals(Phase.OTHER, PhaseClassifier.classify(P + "a/Util.java", "class Util { }"));
    }

    @Test
    void packageFallback() {
        assertEquals(Phase.READ, PhaseClassifier.classify(P + "read/Foo.java", "class Foo { }"));
        assertEquals(Phase.PROCESS, PhaseClassifier.classify(P + "process/Foo.java", "class Foo { }"));
    }

    @Test
    void testsAndOtherFileTypes() {
        assertEquals(Phase.TEST, PhaseClassifier.classify("src/test/java/de/x/FooTest.java", "class FooTest { }"));
        assertEquals(Phase.TEST, PhaseClassifier.classify("mod/src/test/java/FooTest.java", "class FooTest { }"));
        assertEquals(Phase.WRITE, PhaseClassifier.classify("sql/a.sql", "INSERT INTO t VALUES (1)"));
        assertEquals(Phase.MODEL, PhaseClassifier.classify("sql/a.sql", "create table t (a int)"));
        assertEquals(Phase.READ, PhaseClassifier.classify("sql/a.sql", "select a from t"));
        assertEquals(Phase.CONFIG, PhaseClassifier.classify("application.yml", "a: b"));
        assertEquals(Phase.CONFIG, PhaseClassifier.classify("a.properties", "a=b"));
        assertEquals(Phase.CONFIG, PhaseClassifier.classify("ctx.xml", "<beans></beans>"));
        assertEquals(Phase.OTHER, PhaseClassifier.classify("README.md", "x"));
    }
}
