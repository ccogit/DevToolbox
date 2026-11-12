package de.devtoolbox.tools.speccheck;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskListsAndCodeContextTest {

    @Test
    void readsTargetListsOfAllCoreTablesButNotSideTables() {
        String task = """
                ## Soll-Listen

                ### Soll Read – 3 Zeilen (R1–R3)

                #### Tabelle ALTB.A – 2 Zeilen (R1–R2)

                | Nr | Schlüssel | Typ |
                |---|---|---|
                | R1 | A.VNR | CHAR |
                | R2 | A.ART | CHAR |

                #### Tabelle ALTB.B – 1 Zeilen (R3)

                | Nr | Schlüssel | Typ |
                |---|---|---|
                | R3 | B.VNR | CHAR |

                ### Nebentabellen (kein Soll)

                | Nr | Schlüssel | Typ |
                |---|---|---|
                | X1 | C.NEBEN | CHAR |

                ### Soll Process – 1 Zeilen (P1)

                | Nr | Schlüssel | Angaben im Zielmodell |
                |---|---|---|
                | P1 | risikoId | typ: String |

                ## Ergebnis
                """;
        TaskLists lists = TaskLists.parse(List.of(task));
        assertEquals(List.of("A.VNR", "A.ART", "B.VNR"), lists.of(SpecPhase.READ).stream().map(TaskLists.Soll::key).toList());
        assertEquals("R3", lists.of(SpecPhase.READ).get(2).nr());
        assertEquals(1, lists.of(SpecPhase.PROCESS).size());
        assertTrue(lists.of(SpecPhase.WRITE).isEmpty());
    }

    @Test
    void findsFilesAndMethodsInBlocks() {
        String code = "intro\n===== BEGIN FILE: src/a/Foo.java =====\n```java\nclass Foo { void bar(int x) {} }\n```\n"
                + "===== END FILE: src/a/Foo.java =====\n===== BEGIN FILE: res/q.sql =====\nselect 1\n===== END FILE: res/q.sql =====\n";
        CodeContext ctx = CodeContext.parse(List.of(code));
        assertEquals(2, ctx.size());
        assertEquals(1, ctx.classSources("Foo").size());
        assertTrue(ctx.classSources("Bar").isEmpty());
        assertTrue(CodeContext.hasMethod(ctx.classSources("Foo").getFirst(), "bar"));
        assertFalse(CodeContext.hasMethod(ctx.classSources("Foo").getFirst(), "ba"));
        assertTrue(ctx.hasFile("q.sql"));
        assertFalse(ctx.hasFile("x.sql"));
    }
}
