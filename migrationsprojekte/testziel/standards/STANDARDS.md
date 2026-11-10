# Standards – TESTZIEL – Wohngebäude neu – Testdaten

- Beispiel-Entität: **Vertrag**
- Quelle: `C:\Data\Projects\Trials\DevToolbox\testprojekt-ziel`
- Erzeugt: 2026-11-10 20:59

Diese Datei und die Vorlagen sind die **Standards** für die Implementierung weiterer Entitäten und dürfen bearbeitet werden: Zeilen ändern, löschen oder ergänzen, Vorlagen bereinigen, in „Hinweise“ Vorgaben für Copilot notieren. Platzhalter: `{{Entitaet}}` (z. B. Risiko), `{{entitaet}}` (risiko), `{{ENTITAET}}` (RISIKO). Felder und fachliche Logik in den Vorlagen stammen von der Beispiel-Entität und werden bei der Implementierung durch die Attribute der Spezifikation ersetzt.

## Artefakte

| Nr | Phase | Artefakt | Vorlage | Klasse | Package | Basisklasse / Schnittstellen | Klassen-Annotationen | Hinweise |
|---|---|---|---|---|---|---|---|---|
| 1 | Read | Transferobjekt (TO) | `vorlagen/{{Entitaet}}TO.java` | `{{Entitaet}}TO` | `de.vers.neu.{{entitaet}}.to` | `AbstractTO` | – |  |
| 2 | Read | Persistenzobjekt (PO) | `vorlagen/{{Entitaet}}PO.java` | `{{Entitaet}}PO` | `de.vers.neu.{{entitaet}}.po` | `AbstractPO` | – |  |
| 3 | Read | Entity | `vorlagen/{{Entitaet}}Entity.java` | `{{Entitaet}}Entity` | `de.vers.neu.{{entitaet}}.entity` | – | `@Entity`, `@Table` |  |
| 4 | Read | Repository (Einlesen) | `vorlagen/{{Entitaet}}Repository.java` | `{{Entitaet}}Repository` | `de.vers.neu.{{entitaet}}.repository` | `JpaRepository<{{Entitaet}}PO, String>` | – |  |
| 5 | Read | Einleselogik | `vorlagen/{{Entitaet}}Anreicherer.java` | `{{Entitaet}}Anreicherer` | `de.vers.neu.{{entitaet}}.anreicherung` | `Anreicherer<{{Entitaet}}TO>` | – |  |
| 6 | Read | Einleselogik | `vorlagen/{{Entitaet}}PoRowMapper.java` | `{{Entitaet}}PoRowMapper` | `de.vers.neu.{{entitaet}}.read` | `RowMapper<{{Entitaet}}PO>` | – |  |
| 7 | Process | Verarbeitungslogik (Mapping) | `vorlagen/{{Entitaet}}Mapper.java` | `{{Entitaet}}Mapper` | `de.vers.neu.{{entitaet}}.mapping` | – | – |  |
| 8 | Write | Schreiblogik | `vorlagen/{{Entitaet}}Writer.java` | `{{Entitaet}}Writer` | `de.vers.neu.{{entitaet}}.writer` | `ItemWriter<{{Entitaet}}Entity>` | – |  |

## Basisklassen und Schnittstellen

Werden von neuen Klassen verwendet, nicht neu angelegt (Stand bei der Erzeugung, Ordner `basis/`):

- `basis/AbstractTO.java` (src/main/java/de/vers/neu/common/AbstractTO.java)
- `basis/AbstractPO.java` (src/main/java/de/vers/neu/common/AbstractPO.java)
- `basis/Anreicherer.java` (src/main/java/de/vers/neu/common/Anreicherer.java)
