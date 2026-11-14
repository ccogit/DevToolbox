## Logikprotokoll

Die Aufgabe enthält zusätzlich die Liste **Soll Logik** (Nr `L1`, `L2`, …): jede Methode des Code-Kontexts, die
verzweigt, rechnet, Werte nennt oder einen Fehler wirft, mit der ausgezählten Zahl ihrer Verzweigungen und
Fehlerfälle und mit den Werten, die in ihr stehen. Sie ist genauso verbindlich wie die Soll-Listen der Attribute.

Das Ergebnis enthält nach der Ablaufübersicht den Abschnitt **Logikprotokoll**. Du schreibst ihn vom Code aus,
Methode für Methode, **bevor** du die Tabellen der Phasen füllst. Dort steht die vollständige Logik; die Tabellen
der Phasen sind danach das Verzeichnis je Attribut.

### Ein Unterabschnitt je Methode

Jede Nr der Soll Logik bekommt eine Überschrift der Ebene 3 aus Nr und Schlüssel, z. B.
`### L3 RisikoMapperStep.ermittleBauartklasse`, in der Reihenfolge der Liste. Darunter:

1. **Zweck** – ein bis zwei Sätze in Fachsprache: Was entscheidet oder berechnet die Methode, für welche Sätze,
   und von wo wird sie aufgerufen?
2. **Regeln** – eine Tabelle mit einer Zeile je Verzweigung, Fehlerfall und Berechnung, in der Reihenfolge des
   Codes:

   | Nr | Bedingung | Ergebnis | Original | Betrifft |
   |---|---|---|---|---|

   - **Nr:** `L3.1`, `L3.2`, … Auf diese Regelnummern verweisen die Tabellen der Phasen.
   - **Bedingung:** fachlich formuliert, mit den konkreten Werten (z. B. „Tarifgeneration vor 2008“). Jeder
     Zweig ist eine eigene Zeile: `if`, `else if`, `else`, jeder `case` und `default`, jeder Filter eines
     Streams, jede vorzeitige Rückkehr. Die Zeile eines „sonst“-Zweigs nennt, welche Fälle übrig bleiben. Gilt
     eine Berechnung immer, steht „immer“.
   - **Ein Ausgang je Zeile.** Schreibe nie „true, sonst false“ oder „Rückgabe des Ergebnisses“ in eine Zeile:
     Jeder Ausgang einer Prüfung ist eine eigene Zeile, auch der negative. Sucht der Code in einer Schleife oder
     einem Stream nach einem Satz, stehen „gefunden“ und „nicht gefunden“ je in einer Zeile, mit dem Kriterium
     der Suche (welche Felder womit verglichen werden).
   - **Ergebnis:** was dann geschieht – welches Feld mit welchem Wert oder welcher Formel belegt wird, welcher
     Satz verworfen wird, welcher Fehler entsteht.
   - **Original:** der Ausdruck der Bedingung bzw. der Zuweisung **wörtlich aus dem Code**, höchstens eine
     Zeile (z. B. `` `tarifgeneration < 2008` ``). Kein Ausdruck, den du nicht im Code-Kontext gelesen hast.
   - **Betrifft:** die Nr der Soll-Zeile, die die Regel bestimmt (z. B. `P2`), `Satz` für eine Regel über den
     ganzen Satz (sie wird eine Satzregel) oder `–`.
   - Die Tabelle hat **mindestens** so viele Zeilen, wie die Soll Logik für die Methode „Verzweigungen“ plus
     „Fehler“ nennt. Sind es weniger, steht unter der Tabelle in einem Satz, warum.
3. **Werte** – jeder Wert aus der Spalte „Werte im Code“ der Soll Logik und jede verwendete Konstante der Liste
   „Soll Konstanten“, wörtlich: | Wert | Bedeutung | Verwendet in (Regel-Nr) |. Zeigt der Code die Bedeutung
   nicht, steht „Bedeutung offen“ und eine OPL-Nr. Codelisten und Enum-Zuordnungen stehen vollständig da, ein
   Wertepaar je Zeile. Prüft eine Regel gegen eine Liste oder Menge (z. B. `beitragsFreieGruende.contains(…)`),
   steht ihr Inhalt hier Wert für Wert; zeigt der Code-Kontext den Inhalt nicht, nenne, woher die Liste kommt,
   und lege einen OPL-Punkt an.
4. **Fehler und Abbruch** – je Fehlerfall: Typ der Exception, Meldungstext wörtlich, und was mit dem Satz bzw.
   dem Lauf geschieht. Gibt es keinen, steht „keine“.
5. **Aufrufe** – aufgerufene Methoden mit eigener Nr der Soll Logik (nur die Nr) und Lookups, Schlüsseltabellen
   oder Services, deren Inhalt der Code-Kontext nicht zeigt (je einer ein OPL-Punkt).

Ist eine Methode für die Entität oder die Phasen der Aufgabe ohne Belang, bleibt ihr Unterabschnitt stehen und
enthält nur die Zeile `nicht relevant: <Grund>`. Keine Nr der Soll Logik fehlt.

### Beispiel für die erwartete Tiefe

```markdown
### L3 RisikoMapperStep.ermittleBauartklasse

**Zweck:** Bestimmt die Bauartklasse des Gebäudes aus dem Schlüssel der Altdaten. Wird je Risiko aus
`RisikoMapperStep.map` aufgerufen, bevor der Beitrag berechnet wird.

| Nr | Bedingung | Ergebnis | Original | Betrifft |
|---|---|---|---|---|
| L3.1 | Bauart-Schlüssel ist leer | Bauartklasse = BAK1 (Default) | `bauart == null \|\| bauart.isBlank()` | P4 |
| L3.2 | Tarifgeneration vor 2008 | Bauartklasse = UNDEFINIERT, der Schlüssel der Altdaten wird in „Altvertragsgrund“ abgelegt | `tarifgeneration < 2008` | P4, P9 |
| L3.3 | Schlüssel ist 10, 11 oder 12 | Bauartklasse = BAK1 | `case "10", "11", "12"` | P4 |
| L3.4 | Schlüssel ist 20 | Bauartklasse = BAK2 | `case "20"` | P4 |
| L3.5 | sonst (jeder andere Schlüssel) | Fehler, der Vertrag wird nicht migriert | `default -> throw new BusinessException(...)` | Satz |

**Werte**

| Wert | Bedeutung | Verwendet in |
|---|---|---|
| 2008 | erste Tarifgeneration mit Bauartklassen | L3.2 |
| "10", "11", "12" | massive Bauweise | L3.3 |
| "20" | Bedeutung offen (OPL-4) | L3.4 |

**Fehler und Abbruch:** `BusinessException` „Unbekannte Bauart: <Schlüssel>“ – der Vertrag wird abgewiesen und
im Fehlerprotokoll geführt.

**Aufrufe:** L7 (Beitragsberechnung); Lookup `SchluesselService.bauart` – Werte nicht im Code-Kontext (OPL-5).
```

Das Beispiel zeigt nur Form und Tiefe; sein Inhalt ist erfunden und gehört nicht ins Ergebnis.

### Tabellen der Phasen

- Die Zelle „Einlesen“, „Mapping“ bzw. „Schreiben“ fasst die Regeln des Attributs in ein bis drei Sätzen mit
  den konkreten Bedingungen und Werten zusammen. Das ersetzt die Vorgabe des Detailgrads für die Zelle und ist
  die einzige erlaubte Doppelung.
- Die Spalte „Beleg“ nennt die **Regelnummern** des Logikprotokolls (z. B. `L3.1, L3.2, L7.4`) statt
  `Klasse.Methode`. Jede Regel, deren Spalte „Betrifft“ die Zeile nennt, steht dort.
- Jede Regel mit „Betrifft: Satz“ ist in genau einer Satzregel belegt.
- Bestimmt keine Regel des Logikprotokolls ein Attribut und wird es nur unverändert übernommen, nennt „Beleg“
  wie bisher `Klasse.Methode`.

### Umfang

Für die Länge gibt es keine Obergrenze. Ein Unterabschnitt ist erst fertig, wenn ein Entwickler die Methode ohne
Blick in das Referenzprojekt nachbauen könnte. Fasse keine Zweige zusammen und lass keinen weg, weil er selten
ist. Reicht die Ausgabe nicht, höre nach einem vollständigen Unterabschnitt auf und melde `UNVOLLSTÄNDIG` mit der
letzten vollständigen Nr der Soll Logik, statt die übrigen Unterabschnitte zu kürzen.
