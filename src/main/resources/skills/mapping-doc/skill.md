# Skill: Mapping-Dokumentation aus dem implementierten Code

Dieser Skill beschreibt, **wie** du eine Aufgabe vom Typ „Mapping-Dokumentation“ bearbeitest. **Was** zu
dokumentieren ist – Entität, Vorlage, Code –, steht in der Aufgabe-Datei („Aufgabe: …“).

## Ziel

Nach Abschluss der Implementierung wird für jede Entität das **tatsächlich implementierte Mapping** in einer
Vorlage dokumentiert. Die Vorlage ist eine Confluence-Tabelle (Wiki-Markup) mit einer Zeile je Attribut des
Zielmodells. Du füllst darin die Spalten **„Mapping“** (regulärer Bestand) und **„Schwebe Mapping“** (Schwebe,
d. h. noch nicht wirksame, schwebende Vertragsstände) und gibst die ausgefüllte Vorlage als Datei zurück.

## Eingaben

- **Vorlage** (Datei `mapping-vorlage-….txt` bzw. `.md`): die Tabelle, die du ausfüllst. Spalten typischerweise
  `Entität | Attribut | Mapping | Schwebe Mapping | Spezifikation | Status`. Eine leere Zelle „Entität“ gehört zur
  Entität der Zeile darüber. Platzhalter wie `{status:colour=Red|title=TBD|subtle=true}` sind Confluence-Makros.
- **Aufgabe** mit
  - der **Voranalyse**: je Attribut die Fundstellen im Process-Code (Klasse.Methode:Zeile) und ob die Methode
    Schwebe-Bezug hat, dazu alle Zeilen des Process-Codes, die „Schwebe“ erwähnen. Sie ist heuristisch – ein
    Wegweiser, kein Beweis.
  - dem **Code** der Process-Phase (Mapping/Verarbeitung) und der Datenstrukturen (TOs, POs, Entities …) mit klaren
    Dateigrenzen (`===== BEGIN FILE: … =====` / `===== END FILE: … =====`). **Der Code ist die einzige Quelle**
    für den Inhalt der Dokumentation.

## Bestand und Schwebe

1. Finde zuerst heraus, **wo** der Code Schwebe vom Bestand unterscheidet: Merkmale wie `SCHWEBE_MM`,
   `isSchwebe()`, Parameter oder Felder mit „schwebe“, eigene Methoden, Klassen oder Steps für Schwebe, andere
   Quellobjekte. Ausgangspunkt sind die „Schwebe-Stellen“ der Voranalyse.
2. **Regelfall:** Schwebe wird genauso gemappt wie der Bestand. Dann steht in „Schwebe Mapping“ nur `wie Bestand`.
3. **Sonderweg:** Nur wenn der Code für Schwebe nachweislich etwas anderes tut (andere Quelle, andere Regel,
   anderer Default, Attribut wird nicht gesetzt …), beschreibst du in „Schwebe Mapping“ vollständig, was für Schwebe
   gilt, mit eigener Fundstelle. Jeder behauptete Unterschied braucht eine Fundstelle im Code.
4. Verarbeitet der Code Schwebe für diese Entität gar nicht, steht in „Schwebe Mapping“ `kein Mapping (Schwebe wird
   nicht verarbeitet)` mit der Fundstelle, die das belegt; ist das nicht belegbar, `[PRÜFEN] …`.
5. Beachte die Richtung von Bedingungen: In `if (schwebe) { A } else { B }` gilt A für Schwebe und B für den
   Bestand. Die Voranalyse markiert beide Zweige als „Schwebe-Bezug“ – entscheide anhand des Codes.

## Inhalt einer Mapping-Zelle

Knapp, fachlich lesbar, aber vollständig nachvollziehbar – in dieser Reihenfolge:

- **Quelle:** Feld des Quellobjekts (`AltXyzTO.feld`); wenn im Code erkennbar (RowMapper, `@Column`, SQL,
  Konstanten) zusätzlich Tabelle.Spalte, z. B. `T951V0.BSZ_ZUS_PROZ`. Mehrere Quellen aufzählen.
- **Regel:** `1:1`, Umschlüsselung (mit den Werten, z. B. `'Z' → ZUSCHLAG, 'R' → NACHLASS`), Berechnung (Formel),
  Konstante/Default, Bedingung (wann gesetzt, wann nicht), Formatierung/Typumwandlung.
- **Fundstelle:** `Klasse.methode` (Zeile nur, wenn hilfreich).

Beispiel (eine Zelle): `{{AltZuschlagTO.zuschlagProzent}} (T951V0.BSZ_ZUS_PROZ) 1:1, nur wenn > 0, sonst
{{BSZ_RAB_PROZ}} \\ Fundstelle: {{NachlassZuschlagMapper.mappe}}`

Sonderfälle:

| Fall | Inhalt der Zelle |
|---|---|
| Attribut wird im Code nicht gesetzt | `nicht gemappt` (mit Grund, falls erkennbar, z. B. „bleibt null“, „Default aus Entity“) |
| Wert ist eine Konstante | `Konstante 'X'` + Fundstelle |
| Wert wird an anderer Stelle gesetzt (z. B. Writer, Datenbank-Default) und das ist im Code sichtbar | Beschreibung + Fundstelle |
| Mapping ist im vorliegenden Code nicht eindeutig erkennbar | `[PRÜFEN] <was unklar ist>` + deine beste Lesart |

## Arbeitsregeln

1. **Nur Implementiertes dokumentieren.** Keine Annahmen aus Namen, keine Inhalte aus Spezifikationen oder
   Erfahrung. Was der Code nicht belegt, ist `[PRÜFEN]`.
2. **Jedes Attribut der Vorlage** bekommt in „Mapping“ und „Schwebe Mapping“ einen Inhalt – auch `nicht gemappt`
   oder `wie Bestand`. Kein TBD-Makro bleibt in diesen beiden Spalten stehen.
3. **Struktur unverändert lassen:** Kopfzeile, Spaltenreihenfolge, Zeilenreihenfolge, Entität, Attribut und alle
   übrigen Spalten (z. B. „Spezifikation“, „Status“) bleiben **zeichengenau** wie in der Vorlage, ebenso Text vor
   und nach der Tabelle. Keine Zeilen hinzufügen oder löschen.
4. **Eine Tabellenzeile = eine Textzeile.** Ist eine Zeile der Vorlage umbrochen, gibst du sie zusammengefügt in
   einer Zeile aus.
5. **Confluence-Wiki-Syntax in Zellen:** kein Zeilenumbruch – stattdessen ` \\ `; ein senkrechter Strich im Text
   als `\|`; Code-Namen als `{{Name}}` (Monospace); keine Markdown-Syntax (kein `**`, keine Backticks).
6. **Attribute ohne Zeile:** Setzt der Code Zielfelder, die in der Vorlage fehlen, fügst du sie **nicht** ein,
   sondern nennst sie in der Chat-Antwort.

## Vorgehen

1. Lies die Vorlage und notiere die Attribute.
2. Lies den Process-Code: Einstieg (Processor/Mapper), Ablauf, Hilfsmethoden, Umschlüsselungen, Konstanten.
3. Kläre die Unterscheidung Bestand/Schwebe (siehe oben).
4. Gehe die Attribute der Vorlage der Reihe nach durch: Fundstellen aus der Voranalyse prüfen, fehlende selbst im
   Code suchen (Konstruktor, Builder, generisches Mapping, abweichende Namen), dann beide Zellen füllen.
5. Prüfe: keine TBD-Makros mehr in „Mapping“/„Schwebe Mapping“, Struktur unverändert, jede Zeile eine Textzeile.

## Ausgabeformat

**Abgabe als Datei:** Erzeuge die ausgefüllte Vorlage unter dem Dateinamen aus der Aufgabe (Abschnitt „Abgabe“)
und stelle sie zum **Download** bereit – reiner Text im Wiki-Markup der Vorlage, ohne Codeblock-Zäune und ohne
Zusätze.

Im Chat steht nur eine kurze Übersicht: Anzahl dokumentierter Attribute, Attribute mit Schwebe-Sonderweg (Liste),
Attribute `nicht gemappt` (Liste), alle `[PRÜFEN]`-Punkte (Attribut + Frage) und im Code gesetzte Zielfelder ohne
Zeile in der Vorlage. Den Tabelleninhalt wiederholst du dort nicht.

Kannst du keine Datei erzeugen, gib stattdessen den vollständigen Inhalt der ausgefüllten Vorlage in **einem**
Codeblock aus, danach die Übersicht.
