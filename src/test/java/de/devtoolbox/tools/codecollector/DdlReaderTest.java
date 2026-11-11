package de.devtoolbox.tools.codecollector;

import de.devtoolbox.TestSupport;
import de.devtoolbox.tools.codecollector.DdlReader.Column;
import de.devtoolbox.tools.codecollector.DdlReader.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DdlReaderTest {

    private static Table table(List<Table> tables, String name) {
        return tables.stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }

    private static Column column(Table t, String name) {
        return t.columns().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void testprojektDdl() throws Exception {
        List<Table> tables = DdlReader.read(TestSupport.TESTPROJEKT.resolve("ddl"));
        assertEquals(5, tables.size());
        assertEquals(23, tables.stream().mapToInt(t -> t.columns().size()).sum());

        Table risiko = table(tables, "ALT_RISIKO");
        assertEquals("ALTB.ALT_RISIKO", risiko.qualifiedName());
        assertEquals(List.of("VNR", "RISART"), risiko.primaryKey());
        assertEquals(1, risiko.foreignKeys().size());
        assertTrue(risiko.foreignKeys().getFirst().contains("ALT_VERTRAG"));
        assertEquals(8, risiko.columns().size());
        Column art = column(risiko, "RISART");
        assertEquals("Risikoart", art.label());
        assertTrue(art.comment().contains("FE=Feuer"));
        assertFalse(art.nullable());
        assertTrue(column(risiko, "OBERRIS").nullable());
        assertEquals("0", column(risiko, "JAHRESBEITRAG").defaultValue().strip());

        Table objekt = table(tables, "ALT_OBJEKT");
        assertTrue(objekt.primaryKey().isEmpty());
        assertEquals(List.of(List.of("VNR")), objekt.uniqueIndexes(), "unique index declared before the table");

        assertEquals(List.of("NAME"), table(tables, "FAKTOREN").primaryKey());
        assertEquals(List.of("VNR"), table(tables, "ALT_VERTRAG").primaryKey());
    }

    @Test
    void singleFileAndFolderGiveSameResult() throws Exception {
        List<Table> dir = DdlReader.read(TestSupport.TESTPROJEKT.resolve("ddl"));
        List<Table> file = DdlReader.read(TestSupport.TESTPROJEKT.resolve("ddl/altbestand.ddl"));
        assertEquals(dir.size(), file.size());
    }

    @Test
    void bothRemarkSyntaxesAndKeys(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("x.sql");
        Files.writeString(f, """
                CREATE UNIQUE INDEX S.IX ON S.B (K1, K2);
                CREATE TABLE S.A (ID INT NOT NULL, NAME VARCHAR(10), CONSTRAINT PK_A PRIMARY KEY (ID));
                CREATE TABLE S.B (K1 INT NOT NULL, K2 INT NOT NULL, A_ID INT, TXT VARCHAR(5) WITH DEFAULT 'x');
                ALTER TABLE S.B ADD CONSTRAINT FK_B FOREIGN KEY (A_ID) REFERENCES S.A (ID);
                LABEL ON TABLE S.A IS 'Tabelle A';
                COMMENT ON TABLE S.A IS 'Kommentar zu A';
                LABEL ON COLUMN S.A.NAME IS 'Name Label';
                COMMENT ON COLUMN S.A.NAME IS 'it''s a name';
                LABEL ON S.B (K1 IS 'Schluessel 1', K2 IS 'Schluessel 2');
                COMMENT ON S.B (A_ID IS 'Verweis auf A');
                """);
        List<Table> tables = DdlReader.read(f);
        assertEquals(2, tables.size());
        Table a = table(tables, "A");
        assertEquals(List.of("ID"), a.primaryKey());
        assertEquals("Tabelle A", a.label());
        assertEquals("Kommentar zu A", a.comment());
        assertEquals("Name Label", column(a, "NAME").label());
        assertEquals("it's a name", column(a, "NAME").comment());

        Table b = table(tables, "B");
        assertEquals(1, b.foreignKeys().size());
        assertEquals(List.of(List.of("K1", "K2")), b.uniqueIndexes());
        assertEquals("Schluessel 1", column(b, "K1").label());
        assertEquals("Schluessel 2", column(b, "K2").label());
        assertEquals("Verweis auf A", column(b, "A_ID").comment());
        assertEquals("'x'", column(b, "TXT").defaultValue());
    }

    @Test
    void toleratesUnknownStatements(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("x.ddl");
        Files.writeString(f, "-- Kommentar\nGRANT SELECT ON T TO PUBLIC;\nCREATE TABLE T (A INT);\nSELECT 1 FROM T;\n");
        assertEquals(1, DdlReader.read(f).size());
    }

    /** Format of the real project: DB2 views with column list, comments wrapped over lines, no types. */
    @Test
    void db2ViewsWithColumnListAndComments(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("views.sql");
        Files.writeString(f, """
                CREATE VIEW ALTDB.T951V0 (VSNR , STANDORT_NR , BSZ , BSZ_ZUS_RAB_H_BEG ) AS SELECT
                ALL VSNR , STANDORT_NR , BSZ , BSZ_ZUS_RAB_H_BEG FROM DB2T.F951T;

                comment on table ALTDB.T951V0 is 'VHV-Zuschläge / -Rabatte';

                comment on column ALTDB.T951V0.VSNR is 'Versicherungsscheinnummer';

                comment on column ALTDB.T951V0.BSZ_ZUS_RAB_H_BEG is 'Beginn der Gültigkeit der Eintragung dieser Zeile aus dem
                Historienkonzept';

                CREATE VIEW V2 AS SELECT DISTINCT t.A, COALESCE(t.B, 0) AS B_X, u.C FROM S.T t JOIN S.U u ON t.A = u.A;
                """);
        List<Table> tables = DdlReader.read(f);
        assertEquals(2, tables.size());
        Table v = table(tables, "T951V0");
        assertTrue(v.isView());
        assertEquals("ALTDB.T951V0", v.qualifiedName());
        assertEquals("DB2T.F951T", v.viewOf());
        assertEquals("VHV-Zuschläge / -Rabatte", v.comment());
        assertEquals(List.of("VSNR", "STANDORT_NR", "BSZ", "BSZ_ZUS_RAB_H_BEG"), v.columns().stream().map(Column::name).toList());
        assertEquals("Versicherungsscheinnummer", column(v, "VSNR").comment());
        assertEquals("Beginn der Gültigkeit der Eintragung dieser Zeile aus dem Historienkonzept",
                column(v, "BSZ_ZUS_RAB_H_BEG").comment());
        assertEquals("", column(v, "VSNR").type());

        Table v2 = table(tables, "V2");
        assertEquals(List.of("A", "B_X", "C"), v2.columns().stream().map(Column::name).toList());
        assertEquals("S.T, S.U", v2.viewOf());
    }

    @Test
    void viewInheritsTypesAndKeysFromKnownBaseTable(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("x.ddl");
        Files.writeString(f, """
                CREATE VIEW ALTDB.TV (VNR, BETRAG) AS SELECT ALL VNR, BETRAG FROM DB2T.T;
                CREATE TABLE DB2T.T (VNR CHAR(12) NOT NULL, BETRAG DECIMAL(9, 2), PRIMARY KEY (VNR));
                """);
        Table v = table(DdlReader.read(f), "TV");
        assertEquals("CHAR(12)", column(v, "VNR").type());
        assertFalse(column(v, "VNR").nullable());
        assertEquals(List.of("VNR"), v.primaryKey());
        assertFalse(table(DdlReader.read(f), "T").isView());
    }
}
