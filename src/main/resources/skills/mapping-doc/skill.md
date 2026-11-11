# Skill: Mapping-Dokumentation aus dem implementierten Code

Dieser Skill beschreibt, **wie** du eine Aufgabe vom Typ „Mapping-Dokumentation“ bearbeitest. **Was** zu
dokumentieren ist – Entität, Dokumente, Code –, steht in der Aufgabe-Datei („Aufgabe: …“).

## Ziel

Nach Abschluss der Implementierung wird für jede Entität das **tatsächlich implementierte Mapping** dokumentiert,
und zwar je Attribut des Zielmodells getrennt für den **Bestand** und für **Schwebe** (schwebende, noch nicht
wirksame Vertragsstände). Die Dokumentation steht in zwei Dokumenten mit gleichem Inhalt:

| Dokument | Bestand | Schwebe |
|---|---|---|
| **Zielmodell-JSON** der Entität (die Quelle, aus der die Confluence-Seite erzeugt wird) | Feld `mapping` | Feld `schwebeMapping` |
| **Confluence-Tabelle** (Wiki-Markup) | Spalte „Mapping“ | Spalte „Schwebe Mapping“ |

Du trägst die Mapping-Regeln ein und gibst beide Dokumente als Dateien zurück. **Spezifikation** (das
Jira-Spezifikationsticket, `{jiraissues:…}`) und **Status** (`{status:colour=Green|title=FERTIG|subtle=true}`) sind
bereits eingetragen – du änderst sie nicht.

## Eingaben

- **Dokumente** (Datei `vorlagen-mapping-….md`): JSON und Confluence-Tabelle, jeweils zwischen
  `===== BEGIN FILE: <Name> =====` und `===== END FILE: <Name> =====`. `<Name>` ist der Dateiname deines Ergebnisses.
  Fehlt eines der beiden Dokumente, lieferst du nur das vorhandene.
- **Aufgabe** mit
  - der **Voranalyse**: je Attribut die Fundstellen im Process-Code (Klasse.Methode:Zeile) und ob die Methode
    Schwebe-Bezug hat, dazu alle Zeilen des Process-Codes, die „Schwebe“ erwähnen. Sie ist heuristisch – ein
    Wegweiser, kein Beweis.
  - dem **Code**: Process-Phase (Mapping), Read-Phase und Datenzugriff (RowMapper, SQL – daraus ergeben sich die
    Quellspalten) und die Datenstrukturen. **Der Code ist die einzige Quelle** für den Inhalt.

## Bestand und Schwebe

1. Finde zuerst heraus, **wo** der Code Schwebe vom Bestand unterscheidet: Merkmale wie `SCHWEBE_MM`,
   `isSchwebe()`, Parameter oder Felder mit „schwebe“, eigene Methoden, Klassen oder Steps für Schwebe, andere
   Quellobjekte. Ausgangspunkt sind die „Schwebe-Stellen“ der Voranalyse.
2. **Regelfall:** Schwebe wird genauso gemappt wie der Bestand. Dann steht bei Schwebe nur `wie Bestand`.
3. **Sonderweg:** Nur wenn der Code für Schwebe nachweislich etwas anderes tut (andere Quelle, andere Regel,
   anderer Default, Attribut wird nicht gesetzt …), beschreibst du bei Schwebe vollständig die Regel für Schwebe.
   Jeder behauptete Unterschied muss im Code belegbar sein.
4. Verarbeitet der Code Schwebe für diese Entität gar nicht: `kein Mapping (Schwebe wird nicht verarbeitet)`; ist
   das nicht eindeutig belegbar: `[PRÜFEN] …`.
5. Beachte die Richtung von Bedingungen: In `if (schwebe) { A } else { B }` gilt A für Schwebe und B für den
   Bestand. Die Voranalyse markiert beide Zweige als „Schwebe-Bezug“ – entscheide anhand des Codes.

## Formulierung einer Mapping-Regel

Die Regel beschreibt fachlich lesbar, **woraus** und **wie** das Zielattribut befüllt wird. Quellen werden als
**Tabelle.Spalte der Altdatenbank** benannt (aus RowMapper/SQL/`@Column` des Codes; Tabellenname so kurz wie im
Projekt üblich, z. B. `F951.BSZ_SCHL`). Ist keine Spalte erkennbar, das Feld des Quellobjekts.

- Bedingte Logik als **„Wenn …, dann …, ansonsten …“**, z. B.
  `Wenn F951.BSZ_SCHL != null, dann F951.BSZ_SCHL umgeschlüsselt (Z → ZUSCHLAG, R → NACHLASS), ansonsten NACHLASS`.
  Mehrere Bedingungen: `Wenn …, dann …; wenn …, dann …; ansonsten …`.
- Nur wenn der Code wirklich eine Bedingung enthält. Ohne Bedingung schlicht: `1:1 aus F951.BSZ_ZUS_PROZ`,
  `Konstante 'EUR'`, `F951.BSZ_ZUS_BETR * 100`, `Datum aus F951.BSZ_ZUS_RAB_BEG (Format yyyy-MM-dd)`.
- Umschlüsselungen mit ihren Werten, Berechnungen mit Formel, Defaults und Typumwandlungen nennen.
- Keine Klassen- oder Methodennamen, keine Zeilennummern – die Regel richtet sich an Fachleute.

Sonderfälle:

| Fall | Inhalt |
|---|---|
| Attribut wird im Code nicht gesetzt | `nicht gemappt` (mit Grund, falls erkennbar, z. B. „bleibt leer“) |
| Wert wird an anderer Stelle gesetzt (z. B. Writer, Datenbank-Default) und das ist im Code sichtbar | Beschreibung, wo und wie |
| Mapping ist im vorliegenden Code nicht eindeutig erkennbar | `[PRÜFEN] <was unklar ist>` + deine beste Lesart |

## Abgleich mit dem Referenzprojekt (falls in der Aufgabe enthalten)

Am Ende der Aufgabe kann das Mapping des **Referenzprojekts** stehen (Dateien mit Präfix `REFERENZ/`). Es ist
**nie Grundlage** der Regeln – dokumentiert wird ausschließlich, was das Migrationsprojekt implementiert. Es dient
nur dem Vergleich:

- Mappt das Referenzprojekt **dasselbe Attribut fachlich anders** (andere Quelle, Bedingung, Umschlüsselung,
  Berechnung, Default, Schwebe-Behandlung), hängst du an die Regel des Migrationsprojekts an:
  ` – Hinweis: abweichend vom Referenzprojekt (dort: <Regel dort, kurz>)`.
- Kein Hinweis bei gleicher Logik, bei rein technischen Unterschieden (Klassen, Methoden, Namen) oder wenn das
  Attribut im Referenzprojekt nicht vorkommt.
- Liste alle Abweichungen zusätzlich in der Chat-Übersicht.

## Arbeitsregeln

1. **Nur Implementiertes dokumentieren.** Keine Annahmen aus Namen, keine Inhalte aus Spezifikationen oder
   Erfahrung. Was der Code nicht belegt, ist `[PRÜFEN]`.
2. **Jedes Attribut** bekommt eine Regel für Bestand und für Schwebe – auch `nicht gemappt` oder `wie Bestand`.
   Kein TBD-Makro (`{status:…title=TBD…}`) bleibt in `mapping`/`schwebeMapping` bzw. „Mapping“/„Schwebe Mapping“.
3. **JSON und Tabelle stimmen überein:** dieselbe Regel, wortgleich, für dasselbe Attribut.
4. **Struktur unverändert lassen:**
   - JSON: alle Schlüssel, Reihenfolge, Typen (Text bleibt Text, Liste bleibt Liste), `specification` und `status`
     unverändert; nur die Werte von `mapping` und `schwebeMapping` ersetzen. Gültiges JSON (Anführungszeichen im
     Text als `\"`).
   - Tabelle: Kopfzeile, Spalten- und Zeilenreihenfolge, Entität, Attribut, „Spezifikation“, „Status“ und Text
     vor/nach der Tabelle bleiben **zeichengenau**; eine Tabellenzeile = eine Textzeile. Keine Zeilen hinzufügen
     oder löschen.
5. **Text einer Regel:** eine Zeile ohne Zeilenumbruch, kein senkrechter Strich (`|`), keine Markdown-Syntax.
6. **Attribute ohne Eintrag:** Setzt der Code Zielfelder, die in den Dokumenten fehlen, fügst du sie **nicht** ein,
   sondern nennst sie in der Chat-Antwort.

## Vorgehen

1. Lies die Dokumente und notiere die Attribute.
2. Lies den Code: Einstieg der Process-Phase (Processor/Mapper), Ablauf, Hilfsmethoden, Umschlüsselungen,
   Konstanten; in Read/Datenzugriff, aus welchen Spalten die Felder der Quellobjekte stammen.
3. Kläre die Unterscheidung Bestand/Schwebe (siehe oben).
4. Gehe die Attribute der Reihe nach durch: Fundstellen der Voranalyse prüfen, fehlende selbst im Code suchen
   (Konstruktor, Builder, generisches Mapping, abweichende Namen), Regel formulieren, in beide Dokumente eintragen.
5. Prüfe: keine TBD-Makros mehr in den Mapping-Feldern, Struktur unverändert, JSON gültig, JSON und Tabelle gleich.

## Ausgabeformat

**Abgabe als Dateien:** Erzeuge jedes Dokument unter seinem Namen aus der Aufgabe (Abschnitt „Abgabe“) und stelle
es zum **Download** bereit – reiner Dateiinhalt ohne Codeblock-Zäune und ohne Zusätze.

Im Chat steht nur eine kurze Übersicht: Anzahl dokumentierter Attribute, Attribute mit Schwebe-Sonderweg (Liste),
Attribute `nicht gemappt` (Liste), alle `[PRÜFEN]`-Punkte (Attribut + Frage), Abweichungen vom Referenzprojekt
(falls abgeglichen) und im Code gesetzte Zielfelder ohne Eintrag in den Dokumenten. Die Regeln wiederholst du dort
nicht.

Kannst du keine Dateien erzeugen, gib stattdessen jedes Dokument vollständig in **einem eigenen** Codeblock aus
(Überschrift = Dateiname), danach die Übersicht.
