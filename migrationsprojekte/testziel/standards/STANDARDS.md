# Standards – TESTZIEL – Wohngebäude neu – Testdaten

- Beispiel-Entität: **Vertrag**
- Quelle: `C:\Data\Projects\Trials\DevToolbox\testprojekt-ziel`
- Erzeugt: 2026-11-10 21:10

Diese Datei und die Vorlagen sind die **Standards** für die Implementierung weiterer Entitäten und dürfen bearbeitet werden: Zeilen ändern, löschen oder ergänzen, Vorlagen bereinigen, in „Hinweise“ Vorgaben für Copilot notieren. Platzhalter: `{{Entitaet}}` (z. B. Risiko), `{{entitaet}}` (risiko), `{{ENTITAET}}` (RISIKO). Felder und fachliche Logik in den Vorlagen stammen von der Beispiel-Entität und werden bei der Implementierung durch die Attribute der Spezifikation ersetzt.

„Pfad“ ist der Ablageort relativ zum Root des Projekts; die Implementierung legt neue Dateien genau dort ab (bei mehrmoduligen Projekten inkl. Modulordner).

## Artefakte

| Nr | Phase | Artefakt | Vorlage | Klasse | Package | Pfad | Basisklasse / Schnittstellen | Klassen-Annotationen | Hinweise |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Read | Transferobjekt (TO) | `vorlagen/{{Entitaet}}TO.java` | `{{Entitaet}}TO` | `de.vers.neu.{{entitaet}}.to` | `src/main/java/de/vers/neu/{{entitaet}}/to/{{Entitaet}}TO.java` | `AbstractTO` | – |  |
| 2 | Read | Persistenzobjekt (PO) | `vorlagen/{{Entitaet}}PO.java` | `{{Entitaet}}PO` | `de.vers.neu.{{entitaet}}.po` | `src/main/java/de/vers/neu/{{entitaet}}/po/{{Entitaet}}PO.java` | `AbstractPO` | – |  |
| 3 | Read | Entity | `vorlagen/{{Entitaet}}Entity.java` | `{{Entitaet}}Entity` | `de.vers.neu.{{entitaet}}.entity` | `src/main/java/de/vers/neu/{{entitaet}}/entity/{{Entitaet}}Entity.java` | – | `@Entity`, `@Table` |  |
| 4 | Read | Repository (Einlesen) | `vorlagen/{{Entitaet}}Repository.java` | `{{Entitaet}}Repository` | `de.vers.neu.{{entitaet}}.repository` | `src/main/java/de/vers/neu/{{entitaet}}/repository/{{Entitaet}}Repository.java` | `JpaRepository<{{Entitaet}}PO, String>` | – |  |
| 5 | Read | Einleselogik | `vorlagen/{{Entitaet}}Anreicherer.java` | `{{Entitaet}}Anreicherer` | `de.vers.neu.{{entitaet}}.anreicherung` | `src/main/java/de/vers/neu/{{entitaet}}/anreicherung/{{Entitaet}}Anreicherer.java` | `Anreicherer<{{Entitaet}}TO>` | – |  |
| 6 | Read | Einleselogik | `vorlagen/{{Entitaet}}PoRowMapper.java` | `{{Entitaet}}PoRowMapper` | `de.vers.neu.{{entitaet}}.read` | `src/main/java/de/vers/neu/{{entitaet}}/read/{{Entitaet}}PoRowMapper.java` | `RowMapper<{{Entitaet}}PO>` | – |  |
| 7 | Process | Verarbeitungslogik (Mapping) | `vorlagen/{{Entitaet}}Mapper.java` | `{{Entitaet}}Mapper` | `de.vers.neu.{{entitaet}}.mapping` | `src/main/java/de/vers/neu/{{entitaet}}/mapping/{{Entitaet}}Mapper.java` | – | – |  |
| 8 | Write | Schreiblogik | `vorlagen/{{Entitaet}}Writer.java` | `{{Entitaet}}Writer` | `de.vers.neu.{{entitaet}}.writer` | `src/main/java/de/vers/neu/{{entitaet}}/writer/{{Entitaet}}Writer.java` | `ItemWriter<{{Entitaet}}Entity>` | – |  |

## Basisklassen und Schnittstellen

Werden von neuen Klassen verwendet, nicht neu angelegt (Stand bei der Erzeugung, Ordner `basis/`):

- `basis/AbstractTO.java` (src/main/java/de/vers/neu/common/AbstractTO.java)
- `basis/AbstractPO.java` (src/main/java/de/vers/neu/common/AbstractPO.java)
- `basis/Anreicherer.java` (src/main/java/de/vers/neu/common/Anreicherer.java)
